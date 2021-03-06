/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.engine.session;

import ai.grakn.Grakn;
import ai.grakn.GraknGraph;
import ai.grakn.engine.GraknEngineServer;
import ai.grakn.engine.user.UsersHandler;
import ai.grakn.factory.GraphFactory;
import ai.grakn.util.REST;
import mjson.Json;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static ai.grakn.util.REST.RemoteShell.ACTION;
import static ai.grakn.util.REST.RemoteShell.ACTION_INIT;
import static ai.grakn.util.REST.RemoteShell.PASSWORD;
import static ai.grakn.util.REST.RemoteShell.USERNAME;

/**
 * Web socket for running a Graql shell
 */
@WebSocket
public class RemoteSession {
    private final Map<Session, GraqlSession> sessions = new HashMap<>();
    private final Function<String, GraknGraph> getGraph;
    private final Logger LOG = LoggerFactory.getLogger(RemoteSession.class);

    //TODO dont use the default uri
    // This constructor is magically invoked by spark's websocket stuff
    @SuppressWarnings("unused")
    public RemoteSession() {
        this(keyspace -> Grakn.factory(Grakn.DEFAULT_URI, keyspace).getGraph());
    }

    public RemoteSession(Function<String, GraknGraph> getGraph) {
        this.getGraph = getGraph;
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        String message = "Websocket closed, code: " + statusCode + ", reason: " + reason;
        // 1000 = Normal close, 1001 = Going away
        if (statusCode == 1000 || statusCode == 1001) {
            LOG.debug(message);
        } else {
            LOG.error(message);
        }
        sessions.remove(session).close();
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        try {
            Json json = Json.read(message);

            if (json.is(ACTION, ACTION_INIT)) {
                startSession(session, json);
            } else {
                sessions.get(session).handleMessage(json);
            }
        } catch (Throwable e) {
            LOG.error("Exception",e);
            throw e;
        }
    }

    /**
     * Start a new Graql shell session
     */
    private void startSession(Session session, Json json) {
        if (sessionAuthorised(json)) {
            String keyspace = json.at(REST.RemoteShell.KEYSPACE).asString();
            String outputFormat = json.at(REST.RemoteShell.OUTPUT_FORMAT).asString();
            boolean showImplicitTypes = json.at(REST.RemoteShell.IMPLICIT).asBoolean();
            boolean infer = json.at(REST.RemoteShell.INFER).asBoolean();
            GraqlSession graqlSession = new GraqlSession(
                    session, () -> getGraph.apply(keyspace), outputFormat, showImplicitTypes, infer
            );
            sessions.put(session, graqlSession);
        } else {
            session.close(1008, "Unauthorised: incorrect username or password");
        }
    }

    private boolean sessionAuthorised(Json json) {
        if (!GraknEngineServer.isPasswordProtected) return true;

        Json username = json.at(USERNAME);
        Json password = json.at(PASSWORD);

        boolean credentialsProvided = username != null && password != null;

        return credentialsProvided && UsersHandler.getInstance().validateUser(username.asString(), password.asString());

    }
}

