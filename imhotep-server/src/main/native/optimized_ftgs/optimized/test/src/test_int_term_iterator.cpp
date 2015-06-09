#include <algorithm>
#include <iostream>
#include <limits>

#include "shard.hpp"
#include "term_iterator.hpp"

using namespace std;
using namespace imhotep;

int main(int argc, char *argv[])
{
    if (argc != 3) {
        cerr << "usage: " << argv[0] << " <shard dir> <field name>" << endl;
    }

    const Shard  shard(argv[1]);
    const string field_name(argv[2]);

    IntTermIterator it(shard, field_name);
    IntTermIterator end;
    cout << " *it = " << *it << endl;
    cout << "*end = " << *end << endl;
    cout << "it == end? " << (it == end ? "yes" : "no") << endl;
    while (it != end) {
        cout << *it << endl;
        ++it;
    }
}
