#include <algorithm>
#include <array>
#include <cassert>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <limits>
#include <memory>
#include <stdexcept>
#include <string>
#include <tuple>
#include <vector>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>

#define restrict __restrict__
extern "C" {
#include "imhotep_native.h"
#include "metrics_inverter.h"
#include "local_session.h"
#include "test_patch.h"
#include "table.h"
}
#include "test_utils.h"
#include "varintdecode.h"

#include <boost/accumulators/accumulators.hpp>
#include <boost/accumulators/statistics/stats.hpp>
#include <boost/accumulators/statistics/mean.hpp>
#include <boost/accumulators/statistics/variance.hpp>

#include <boost/progress.hpp>

using namespace boost::accumulators;
using namespace std;

class MMappedVarIntView : public VarIntView
{
    int         _fd     = 0;
    size_t      _length = 0;
    void*       _mapped = 0;

public:
    MMappedVarIntView(const MMappedVarIntView& rhs) = delete;

    MMappedVarIntView(const string& filename)
        : _fd(open(filename.c_str(), O_RDONLY)) {
        if (_fd <= 0) {
            throw std::runtime_error("cannot open file: " + filename);
        }

        _length = file_size(_fd);
        _mapped = mmap(0, _length, PROT_READ, MAP_PRIVATE | MAP_POPULATE, _fd, 0);
        if (_mapped == reinterpret_cast<void*>(-1)) {
            throw std::runtime_error("cannot mmap: " + string(strerror(errno)));
        }

        _begin = (const char *) _mapped;
        _end   = _begin + _length;
    }

    ~MMappedVarIntView() {
        munmap(_mapped, _length);
        close(_fd);
    }

    void * mapped() { return _mapped; }

private:
    size_t file_size(int fd) {
        struct stat buf;
        int rc(fstat(fd, &buf));
        if (rc != 0) throw std::runtime_error(strerror(errno));
        return buf.st_size;
    }
};

class IntFieldView {
    static constexpr size_t _n_groups = 30000;

    string _name;

    MMappedVarIntView _terms;
    MMappedVarIntView _docs;

    long _min = numeric_limits<long>::max();
    long _max = numeric_limits<long>::min();

    long _min_doc_id = numeric_limits<long>::max();
    long _max_doc_id = numeric_limits<long>::min();

    long _min_offset = numeric_limits<long>::max();
    long _max_offset = numeric_limits<long>::min();

    long _max_term_doc_freq = 0;

    size_t _n_terms = 0;

public:
    class DocIdIterator {
        VarIntView   _view;
        size_t _remaining = 0;
        long   _doc_id    = 0;
    public:
        DocIdIterator(const VarIntView& view, uint64_t remaining)
            : _view(view)
            , _remaining(remaining)
        { }
        bool has_next() { return _remaining > 0; }
        long next() {
            assert(!_view.empty());
            _doc_id += _view.read_varint<long>(_view.read());
            --_remaining;
            return _doc_id;
        }
    };

    class TermIterator {
        VarIntView _view;
        long _term   = 0;
        long _offset = 0;
    public:
        typedef tuple<long, long, long> Tuple; // term, offset, doc frequency

        TermIterator(const VarIntView& view) : _view(view) { }
        bool has_next() { return !_view.empty(); }

        Tuple next() {
            const long term_delta(_view.read_varint<long>(_view.read()));
            const long offset_delta(_view.read_varint<long>(_view.read()));
            const long doc_freq(_view.read_varint<long>(_view.read()));
            assert(doc_freq > 0);
            _term   += term_delta;
            _offset += offset_delta;
            return Tuple(_term, _offset, doc_freq);
        }
    };

    IntFieldView(const string& shard_dir, const string& name)
        : _name(name)
        , _terms(shard_dir + "/fld-" + name + ".intterms")
        , _docs(shard_dir + "/fld-" + name + ".intdocs") {

        TermIterator it(term_iterator());
        while (it.has_next()) {
            const TermIterator::Tuple next(it.next());
            const long term(get<0>(next));
            const long offset(get<1>(next));
            const long doc_freq(get<2>(next));
            _min = std::min(term, _min);
            _max = std::max(term, _max);
            _min_offset = std::min(offset, _min_offset);
            _max_offset = std::max(offset, _max_offset);
            _max_term_doc_freq = std::max(doc_freq, _max_term_doc_freq);

            VarIntView doc_ids_view(_docs.begin() + offset, _docs.end());
            scrape_doc_ids(doc_ids_view, doc_freq);
            ++_n_terms;
        }
    }

    const string& name() const { return _name; }

    size_t n_groups() const { return _n_groups; }

    long min() const { return _min; }
    long max() const { return _max; }

    long min_doc_id() const { return _min_doc_id; }
    long max_doc_id() const { return _max_doc_id; }

    long min_offset() const { return _min_offset; }
    long max_offset() const { return _max_offset; }

    long max_term_doc_freq() const { return _max_term_doc_freq; }

    int n_rows() const {
        return max_doc_id() + 1;
    }

    size_t n_terms() const { return _n_terms; }

    TermIterator term_iterator() const { return TermIterator(VarIntView(_terms.begin(), _terms.end())); }

    const char* doc_id_stream(long offset) const { return _docs.begin() + offset; };

    void pack(packed_table_t* table, int col, bool group_by_me=false) {
        TermIterator it(term_iterator());
        while (it.has_next()) {
            const TermIterator::Tuple next(it.next());
            const long term(get<0>(next));
            const long offset(get<1>(next));
            const long doc_freq(get<2>(next));
            VarIntView doc_ids_view(_docs.begin() + offset, _docs.end());
            pack(doc_ids_view, doc_freq, table, term, col, group_by_me);
        }
    }

private:
    void scrape_doc_ids(const VarIntView& view, long term_doc_freq) {
        DocIdIterator it(view, term_doc_freq);
        while (it.has_next()) {
            const long doc_id(it.next());
            _min_doc_id = std::min(_min_doc_id, doc_id);
            _max_doc_id = std::max(_max_doc_id, doc_id);
        }
    }

    void pack(const VarIntView& view, long term_doc_freq, packed_table_t* table,
              long term, int col, bool group_by_me) {
        DocIdIterator it(view, term_doc_freq);
        while (it.has_next()) {
            const long doc_id(it.next());
            packed_table_set_cell(table, doc_id, col, term);
            if (group_by_me) {
                packed_table_set_group(table, doc_id, abs(term) % n_groups());
            }
        }
    }
};

ostream&
operator<<(ostream& os, const IntFieldView& view) {
    os << setw(20) << view.name()
       << setw(24) << view.min()
       << setw(24) << view.max()
       << setw(12) << view.min_doc_id()
       << setw(12) << view.max_doc_id()
       << setw(12) << view.n_terms()
       << setw(18) << view.max_term_doc_freq();
    return os;
}

ostream&
operator<<(ostream& os, const vector<shared_ptr<IntFieldView>>& views) {
    os << setw(20) << "name"
       << setw(24) << "min"
       << setw(24) << "max"
       << setw(12) << "min_doc_id"
       << setw(12) << "max_doc_id"
       << setw(12) << "n_terms"
       << setw(18) << "max_term_doc_freq"
       << endl;
    for (auto view: views) os << *view << endl;
    return os;
}

struct TableMetadata {
    int32_t* sizes           = 0;
    int32_t* vec_nums        = 0;
    int32_t* offsets_in_vecs = 0;

    TableMetadata(int n_cols,
                  const int64_t * restrict mins,
                  const int64_t * restrict maxes)
        : sizes(get_sizes(n_cols, mins, maxes))
        , vec_nums(get_vec_nums(n_cols, mins, maxes, sizes))
        , offsets_in_vecs(get_offsets_in_vecs(n_cols, mins, maxes, sizes))
    { }

    ~TableMetadata() {
        free(sizes);
        free(vec_nums);
        free(offsets_in_vecs);
    }

    TableMetadata(const TableMetadata& rhs) = delete;
};


ostream&
operator<<(ostream& os, unpacked_table_t* stats) {
    for (int row(0); row < unpacked_table_get_rows(stats); ++row) {
        for (int col(0); col < unpacked_table_get_cols(stats); ++col) {
            os << setw(20) << unpacked_table_get_cell(stats, row, col);
        }
        os << endl;
    }
    return os;
}

void rrrandom_regroup(packed_table_t* table, int num_docs, int num_grps) {

	for(int i = 0; i < num_docs; i++) {
		const int grp_id = rand() % num_grps;
	    packed_table_set_group(table, i, grp_id);
	}
}


int main(int argc, char* argv[])
{
    simdvbyteinit();

    if (argc < 3) {
        cerr << "usage: dump_term <shard dir> <field> (<field>...)" << endl;
        exit(1);
    }
    string shard_dir(argv[1]);
    vector<string> fields;
    string ftgs_field = argv[2];
    for (int i_argv(2); i_argv < argc; ++i_argv) { fields.push_back(argv[i_argv]); }

    vector<shared_ptr<IntFieldView>> field_views;
    for (auto field: fields) {
        IntFieldView view(shard_dir, field);
        std::cout << view.name() << ':' << std::endl;
        std::cout << "\tmin term: " << view.min() << "    max term: " << view.max() << std::endl;
        std::cout << "\tmin doc id: " << view.min_doc_id() << "    max doc id: " << view.max_doc_id() << std::endl;
        std::cout << "\tmin offset: " << view.min_offset() << "    max offset: " << view.max_offset() << std::endl;
    }

}
