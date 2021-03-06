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

package ai.grakn.graql.internal.printer;

import ai.grakn.concept.Concept;
import ai.grakn.concept.Instance;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.RoleType;
import ai.grakn.concept.Type;
import ai.grakn.graql.Printer;
import ai.grakn.graql.internal.util.ANSI;
import ai.grakn.graql.internal.util.CommonUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ai.grakn.graql.internal.query.match.MatchQueryInternal.colorKeyword;
import static ai.grakn.graql.internal.query.match.MatchQueryInternal.colorType;
import static ai.grakn.graql.internal.util.StringConverter.idToString;
import static ai.grakn.graql.internal.util.StringConverter.valueToString;

/**
 * Default printer that prints results in Graql syntax
 */
class GraqlPrinter implements Printer<Function<StringBuilder, StringBuilder>> {

    private final ResourceType[] resourceTypes;

    GraqlPrinter(ResourceType... resourceTypes) {
        this.resourceTypes = resourceTypes;
    }

    @Override
    public String build(Function<StringBuilder, StringBuilder> builder) {
        return builder.apply(new StringBuilder()).toString();
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlString(boolean inner, Concept concept) {
        return sb -> {
            // Display values for resources and ids for everything else
            if (concept.isResource()) {
                sb.append(colorKeyword("value ")).append(valueToString(concept.asResource().getValue()));
            } else if (concept.isType()) {
                sb.append(colorKeyword("type-name ")).append(colorType(idToString(concept.asType().getName())));
            } else {
                sb.append(colorKeyword("id ")).append(idToString(concept.getId()));
            }

            if (concept.isRelation()) {
                String relationString = concept.asRelation().rolePlayers().entrySet().stream().map(entry -> {
                    RoleType roleType = entry.getKey();
                    Instance rolePlayer = entry.getValue();

                    if (rolePlayer != null) {
                        String s = colorType(idToString(roleType.getName())) + ": id " + idToString(rolePlayer.getId());
                        return Optional.of(s);
                    } else {
                        return Optional.<String>empty();
                    }
                }).flatMap(CommonUtil::optionalToStream).collect(Collectors.joining(", "));

                sb.append(" (").append(relationString).append(")");
            }

            // Display type of each concept
            Type type = concept.type();
            if (type != null) {
                sb.append(colorKeyword(" isa ")).append(colorType(idToString(type.getName())));
            }

            // Display lhs and rhs for rules
            if (concept.isRule()) {
                sb.append(colorKeyword(" lhs ")).append("{ ").append(concept.asRule().getLHS()).append(" }");
                sb.append(colorKeyword(" rhs ")).append("{ ").append(concept.asRule().getRHS()).append(" }");
            }

            // Display any requested resources
            if (concept.isInstance() && resourceTypes.length > 0) {
                concept.asInstance().resources(resourceTypes).forEach(resource -> {
                    String resourceType = colorType(idToString(resource.type().getName()));
                    String value = valueToString(resource.getValue());
                    sb.append(colorKeyword(" has ")).append(resourceType).append(" ").append(value);
                });
            }

            return sb;
        };
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlString(boolean inner, boolean bool) {
        if (bool) {
            return sb -> sb.append(ANSI.color("True", ANSI.GREEN));
        } else {
            return sb -> sb.append(ANSI.color("False", ANSI.RED));
        }
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlString(boolean inner, Optional<?> optional) {
        if (optional.isPresent()) {
            return graqlString(inner, optional.get());
        } else {
            return sb -> sb.append("Nothing");
        }
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlString(boolean inner, Collection<?> collection) {
        return sb -> {
            if (inner) {
                sb.append("{");
                collection.stream().findFirst().ifPresent(item -> graqlString(true, item).apply(sb));
                collection.stream().skip(1).forEach(item -> graqlString(true, item).apply(sb.append(", ")));
                sb.append("}");
            } else {
                collection.forEach(item -> graqlString(true, item).apply(sb).append("\n"));
            }

            return sb;
        };
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlString(boolean inner, Map<?, ?> map) {
        if (!map.entrySet().isEmpty()) {
            Map.Entry<?, ?> entry = map.entrySet().iterator().next();

            // If this looks like a graql result, assume the key is a variable name
            if (entry.getKey() instanceof String && entry.getValue() instanceof Concept) {
                return sb -> {
                    map.forEach((name, concept) ->
                            sb.append("$").append(name).append(" ").append(graqlString(concept)).append("; ")
                    );
                    return sb;
                };
            } else {
                return graqlString(inner, map.entrySet());
            }
        } else {
            return sb -> sb.append("{}");
        }
    }

    @Override
    public Function<StringBuilder, StringBuilder> graqlStringDefault(boolean inner, Object object) {
        if (object instanceof Map.Entry<?, ?>) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) object;
            return graqlString(true, entry.getKey())
                    .andThen(sb -> sb.append(": "))
                    .andThen(graqlString(true, entry.getValue()));
        } else {
            return sb -> sb.append(object.toString());
        }
    }
}
