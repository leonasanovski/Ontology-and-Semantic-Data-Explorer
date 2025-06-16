package mk.finki.finki.mk.semanticsearchapplication.Service;

import org.apache.jena.query.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OntologyService {

    @Value("${ontology.fuseki.endpoint}")
    private String FUSEKI_ENDPOINT;

    private static final Map<String, String> PROPERTY_LABELS = Map.ofEntries(
            Map.entry("http://www.w3.org/2000/01/rdf-schema#label", "Preferred Name"),
            Map.entry("http://www.w3.org/2000/01/rdf-schema#subClassOf", "Subclass Of"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000115", "Definition"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000412", "Ontology Source"),
            Map.entry("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "Type")
    );

    private static final String HAS_CHILD_BOOLEAN = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                ASK { ?child rdfs:subClassOf <%s> . }
            """;

    private static final String TOP_LEVEL_CLASSES = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT ?class ?label WHERE {
                    ?class a owl:Class .
                    FILTER NOT EXISTS { ?class rdfs:subClassOf ?any }
                    OPTIONAL { ?class rdfs:label ?label }
                }
            """;

    private static final String DIRECT_SUBCLASSES = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?class ?label WHERE {
                    ?class rdfs:subClassOf <%s> .
                    OPTIONAL { ?class rdfs:label ?label }
                }
            """;

    private static final String GET_ALL_PROPERTIES_QUERY = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT ?p ?o WHERE {
                    <%s> ?p ?o .
                }
            """;

    private static final String LABELS_IN_RDF = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?label WHERE {
                    <%s> rdfs:label ?label .
                    FILTER(langMatches(lang(?label), 'EN'))
                } LIMIT 1
            """;

    private static final String SUPERCLASS_CHAIN_QUERY = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT DISTINCT ?superclass ?label WHERE {
                    <%s> rdfs:subClassOf+ ?superclass .
                    OPTIONAL { ?superclass rdfs:label ?label . FILTER(langMatches(lang(?label), 'EN')) }
                } ORDER BY ?label
            """;

    public List<Map<String, String>> queryOntology(String searchTerm) {
        String queryStr = String.format("""
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT ?class ?label WHERE {
                    ?class rdf:type owl:Class .
                    ?class rdfs:label ?label .
                    FILTER(CONTAINS(LCASE(STR(?label)), LCASE("%s")))
                } ORDER BY ?label
                """, searchTerm);

        List<Map<String, String>> results = new ArrayList<>();
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String uri = sol.get("class").toString();
                String label = sol.getLiteral("label").getString();

                Map<String, String> result = new HashMap<>();
                result.put("label", label);
                result.put("uri", uri);
                result.put("chain", String.join(" → ", getSuperclassChain(uri)));
                results.add(result);
            }
        }

        return results;
    }

    public Map<String, String> getClassDetails(String uri) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Id", uri);

        String queryStr = String.format(GET_ALL_PROPERTIES_QUERY, uri);
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String rawProp = sol.get("p").toString();
                String value = sol.get("o").toString();
                if (value.startsWith("http")) {
                    String label = getLabelForUri(value);
                    if (label != null) value = label + "|" + value;
                }
                value = value.contains("@") ? value.substring(0, value.lastIndexOf("@")) : value;
                result.put(PROPERTY_LABELS.getOrDefault(rawProp, rawProp), value);
            }
        }

        // Superclass chain (not directly queryable in properties)
        List<String> superclassChain = getSuperclassChain(uri);
        if (!superclassChain.isEmpty()) {
            result.put("Superclass Chain", String.join(";", superclassChain));
        }

        // Notation
        String[] segments = uri.split("[/#]");
        result.put("Notation", segments[segments.length - 1]);

        return result;
    }

    private List<String> getSuperclassChain(String uri) {
        List<String> chain = new ArrayList<>();
        String queryStr = String.format(SUPERCLASS_CHAIN_QUERY, uri);
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String label = sol.contains("label") ? sol.getLiteral("label").getString() : sol.get("superclass").toString();
                String uriPart = sol.get("superclass").toString();
                chain.add(label + "|" + uriPart);
            }
        }
        return chain;
    }

    private String getLabelForUri(String uri) {
        String queryStr = String.format(LABELS_IN_RDF, uri);
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution exec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = exec.execSelect();
            if (rs.hasNext()) {
                QuerySolution sol = rs.next();
                return sol.getLiteral("label").getString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, Object>> getChildrenForUri(String parentUri, String selectedUri) {
        String queryStr = parentUri == null ? TOP_LEVEL_CLASSES : String.format(DIRECT_SUBCLASSES, parentUri);
        List<Map<String, Object>> result = new ArrayList<>();
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution sol = results.next();
                String uri = sol.getResource("class").getURI();
                String label = sol.contains("label") ? sol.getLiteral("label").getString() : uri;
                String text = uri.equals(selectedUri) ? "<b>" + label + "</b>" : label;

                Map<String, Object> node = new HashMap<>();
                node.put("id", uri);
                node.put("text", text);
                node.put("children", hasChildren(uri));
                result.add(node);
            }
        }

        return result;
    }

    private boolean hasChildren(String uri) {
        String queryStr = String.format(HAS_CHILD_BOOLEAN, uri);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, QueryFactory.create(queryStr))) {
            return qexec.execAsk();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getPathToRoot(String uri) {
        List<String> path = new ArrayList<>();
        while (uri != null) {
            path.add(0, uri);
            uri = getDirectSuperclass(uri);
        }
        return path;
    }

    private String getDirectSuperclass(String uri) {
        String queryStr = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "SELECT ?super WHERE { <" + uri + "> rdfs:subClassOf ?super . } LIMIT 1";
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                return rs.next().get("super").toString();
            }
        }
        return null;
    }

    //additional implementation of autosuggestion
    public List<String> getSuggestionsByPrefix(String prefix) {
        String queryStr = String.format("""
                    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                    SELECT DISTINCT ?label WHERE {
                      ?class rdfs:label ?label .
                      FILTER(CONTAINS(LCASE(STR(?label)), LCASE("%s")))
                    } LIMIT 5
                """, prefix);

        List<String> suggestions = new ArrayList<>();
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                if (sol.contains("label")) {
                    suggestions.add(sol.getLiteral("label").getString());
                }
            }
        }
        return suggestions;
    }
}
