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

package ai.grakn.factory;

import ai.grakn.graph.internal.GraknTinkerGraph;
import ai.grakn.util.Schema;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A graph factory which provides a grakn graph with a tinker graph backend.
 */
class TinkerInternalFactory extends AbstractInternalFactory<GraknTinkerGraph, TinkerGraph> {
    private final Logger LOG = LoggerFactory.getLogger(TinkerInternalFactory.class);

    TinkerInternalFactory(String keyspace, String engineUrl, String config){
        super(keyspace, engineUrl, config);
    }

    @Override
    boolean isClosed(TinkerGraph innerGraph) {
        return !innerGraph.traversal().V().has(Schema.ConceptProperty.NAME.name(), Schema.MetaSchema.ENTITY_TYPE.getName()).hasNext();
    }

    @Override
    GraknTinkerGraph buildGraknGraphFromTinker(TinkerGraph graph, boolean batchLoading) {
        return new GraknTinkerGraph(graph, super.keyspace, super.engineUrl, batchLoading);
    }

    @Override
    TinkerGraph buildTinkerPopGraph(boolean batchLoading) {
        LOG.warn("In memory Tinkergraph ignores the address [" + super.engineUrl + "] and " +
                 "the config path [" + super.config + "]");
        return TinkerGraph.open();
    }

    @Override
    protected TinkerGraph getTinkerPopGraph(TinkerGraph graph, boolean batchLoading){
        if(super.graph == null || isClosed(super.graph)){
            super.graph = buildTinkerPopGraph(batchLoading);
        }
        return super.graph;
    }
}
