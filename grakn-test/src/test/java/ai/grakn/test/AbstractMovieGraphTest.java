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

package ai.grakn.test;

import ai.grakn.GraknGraph;
import ai.grakn.GraknGraphFactory;
import ai.grakn.example.MovieGraphFactory;
import org.junit.After;
import org.junit.Before;

/**
 * Abstract test class that uses the movie graph, automatically rolling back after every test to a fresh movie graph.
 * Do not commit to this graph, because it is shared between all tests for performance!
 */
public abstract class AbstractMovieGraphTest extends AbstractEngineTest {

    protected static GraknGraphFactory factory;
    protected static GraknGraph graph;

    @Before
    public void createGraph() {
        if (factory == null || graph == null) {
            factory = factoryWithNewKeyspace();
            graph = factory.getGraph();
            MovieGraphFactory.loadGraph(graph);
        }
    }

    @After
    public final void rollbackGraph() {
        if (usingTinker()) {
            // If using tinker, make a fresh graph
            factory = null;
            graph = null;
        } else {
            try {
                graph.rollback();
            } catch (UnsupportedOperationException e) {
                // If operation unsupported, make a fresh graph
                factory = null;
                graph = null;
            }
        }
    }

}
