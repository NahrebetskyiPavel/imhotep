package com.indeed.imhotep.commands;

import com.indeed.imhotep.api.ImhotepSession;
import com.indeed.imhotep.io.RequestTools;
import com.indeed.imhotep.protobuf.ShardBasicInfoMessage;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@ToString
public class OpenSession extends VoidAbstractImhotepCommand {
    public final OpenSessionData openSessionData;
    public final List<ShardBasicInfoMessage> shards;
    public final int clientVersion;

    public OpenSession(
            final String sessionId,
            final OpenSessionData openSessionData,
            final List<ShardBasicInfoMessage> shards,
            final int clientVersion
    ) {
        super(sessionId, Collections.emptyList(), Collections.singletonList());
        this.openSessionData = openSessionData;
        this.shards = shards;
        this.clientVersion = clientVersion;
    }

    @Override
    public void applyVoid(final ImhotepSession imhotepSession) {
        throw new IllegalStateException("Should not call apply() on OpenSession");
    }

    @Override
    protected RequestTools.ImhotepRequestSender imhotepRequestSenderInitializer() {
        // If you're using this, you should almost certainly be using OpenSessions instead.
        throw new IllegalStateException("Sholud not be trying to send OpenSession directly");
    }
}
