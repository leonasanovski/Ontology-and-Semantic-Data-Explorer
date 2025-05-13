package mk.finki.finki.mk.semanticsearchapplication.Service;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OntologyService {

    private static final String FUSEKI_ENDPOINT = "http://localhost:3030/food_DB/query";
    private static final Map<String, String> PROPERTY_LABELS = Map.ofEntries(
            Map.entry("http://www.w3.org/2000/01/rdf-schema#label", "Preferred Name"),
            Map.entry("http://www.w3.org/2000/01/rdf-schema#subClassOf", "Subclass Of"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000115", "Definition"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000412", "Ontology Source"),
            Map.entry("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "Type")
    );


    public List<Map<String, Object>> getFullOntologyTree(String selectedUri) {
        return getChildrenRecursive(null, selectedUri);
    }

    private List<Map<String, Object>> getChildrenRecursive(String parentUri, String selectedUri) {
        String queryStr;

        if (parentUri == null) {
            // Top-level classes (no superclasses)
            queryStr = """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            SELECT ?class ?label WHERE {
                ?class a owl:Class .
                FILTER NOT EXISTS { ?class rdfs:subClassOf ?any }
                OPTIONAL { ?class rdfs:label ?label }
            }
        """;
        } else {
            // Children of the current class
            queryStr = String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            SELECT ?class ?label WHERE {
                ?class rdfs:subClassOf <%s> .
                OPTIONAL { ?class rdfs:label ?label }
            }
        """, parentUri);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Query query = QueryFactory.create(queryStr);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet results = qexec.execSelect();

            while (results.hasNext()) {
                QuerySolution sol = results.next();

                String classUri = sol.getResource("class").getURI();
                String label = sol.contains("label") ? sol.getLiteral("label").getString() : classUri;

                Map<String, Object> node = new HashMap<>();
                node.put("id", classUri);
                node.put("text", classUri.equals(selectedUri) ? "<b>" + label + "</b>" : label);
                node.put("children", getChildrenRecursive(classUri, selectedUri));

                // Automatically open path leading to selected class
                if (classUri.equals(selectedUri) || isAncestorOf(selectedUri, classUri)) {
                    node.put("state", Map.of("opened", true));
                }

                result.add(node);
            }
        } catch (Exception e) {
            System.err.println("SPARQL ERROR for parentUri = " + parentUri);
            e.printStackTrace();
        }

        return result;
    }

    private boolean isAncestorOf(String selectedUri, String candidateAncestor) {
        String queryStr = String.format("""
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        ASK {
            <%s> rdfs:subClassOf+ <%s> .
        }
    """, selectedUri, candidateAncestor);

        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, QueryFactory.create(queryStr))) {
            return qexec.execAsk();
        } catch (Exception e) {
            System.err.println("Failed to check ancestry for: " + candidateAncestor);
            e.printStackTrace();
            return false;
        }
    }

    private List<String> getSuperclassChain(String uri) {
        List<String> chain = new ArrayList<>();

        String queryStr = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "SELECT DISTINCT ?superclass ?label WHERE { " +
                "  <" + uri + "> rdfs:subClassOf+ ?superclass . " +
                "  ?superclass rdfs:label ?label . " +
                "  FILTER(langMatches(lang(?label), 'EN')) } ORDER BY ?label";

        Query query = QueryFactory.create(queryStr);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String label = sol.getLiteral("label").getString();
                chain.add(label);
            }
        }

        return chain;
    }
    public List<Map<String, String>> queryOntology(String searchTerm) {
        List<Map<String, String>> results = new ArrayList<>();

        String sparql = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
                "SELECT ?class ?label WHERE { " +
                "  ?class rdf:type owl:Class . " +
                "  ?class rdfs:label ?label . " +
                "  FILTER(CONTAINS(LCASE(str(?label)), \"" + searchTerm.toLowerCase() + "\")) " +
                "} ORDER BY ?label";

        Query query = QueryFactory.create(sparql);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String uri = sol.get("class").toString();
                String label = sol.getLiteral("label").getString();

                Map<String, String> result = new HashMap<>();
                result.put("label", label);
                result.put("uri", uri);

                // Fetch the superclass chain for this match
                List<String> superChain = getSuperclassChain(uri);
                result.put("chain", String.join(" → ", superChain));

                results.add(result);
            }
        }

        return results;
    }

    private List<String> getSuperclassChainFromUri(String uri) {
        List<String> chain = new ArrayList<>();
        getSuperRecursive(uri, chain);
        Collections.reverse(chain);
        return chain;
    }

    private void getSuperRecursive(String currentUri, List<String> chain) {
        String queryStr =
                "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                        "SELECT ?superclass ?label WHERE { " +
                        "  <" + currentUri + "> rdfs:subClassOf ?superclass . " +
                        "  OPTIONAL { ?superclass rdfs:label ?label . FILTER(langMatches(lang(?label), \"EN\")) } " +
                        "} LIMIT 1";
        Query query = QueryFactory.create(queryStr);
        try (QueryExecution qe = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String superUri = sol.get("superclass").toString();
                String label = sol.contains("label") ? sol.getLiteral("label").getString() : superUri;
                chain.add(label + "|" + superUri);
                getSuperRecursive(superUri, chain);
            }
        }
    }


    private List<String> getSynonyms(String uri){
        List<String> synonyms = new ArrayList<>();

        String synonymQueryStr =
                "PREFIX obo: <http://purl.obolibrary.org/obo/> " +
                        "SELECT ?synonym WHERE { " +
                        "<" + uri + "> obo:IAO_0000111 ?synonym . " +
                        "FILTER(langMatches(lang(?synonym), 'EN')) }";

        Query synonymQuery = QueryFactory.create(synonymQueryStr);
        try (QueryExecution synExec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, synonymQuery)) {
            ResultSet synRs = synExec.execSelect();
            while (synRs.hasNext()) {
                QuerySolution synSol = synRs.next();
                String synonym = synSol.getLiteral("synonym").getString();
                if (synonym.contains("@")) {
                    synonym = synonym.substring(0, synonym.lastIndexOf("@"));
                }
                synonyms.add(synonym);
            }
        }
        return synonyms;
    }

    private List<String> getAlternativeLabels(String uri) {
        List<String> alternatives = new ArrayList<>();
        String queryStr =
                "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
                        "SELECT ?alt WHERE { " +
                        "<" + uri + "> skos:altLabel ?alt . " +
                        "FILTER(langMatches(lang(?alt), 'EN')) }";

        Query query = QueryFactory.create(queryStr);
        try (QueryExecution exec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = exec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String alt = sol.getLiteral("alt").getString();
                if (alt.contains("@")) {
                    alt = alt.substring(0, alt.lastIndexOf("@"));
                }
                alternatives.add(alt);
            }
        }
        return alternatives;
    }

    public Map<String, String> getClassDetails(String uri) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Id", uri);

        String sparql = "SELECT ?prop ?value WHERE { " +
                "<" + uri + "> ?prop ?value " +
                "}";

        Query query = QueryFactory.create(sparql);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String rawProp = sol.get("prop").toString();
                String value = sol.get("value").toString();
                String readableLabel = PROPERTY_LABELS.getOrDefault(rawProp, rawProp);
                if (value.startsWith("http")) {
                    String labelQueryStr =
                            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                                    "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
                                    "SELECT ?label WHERE { " +
                                    "  OPTIONAL { <" + value + "> a owl:Class . } " +
                                    "  <" + value + "> rdfs:label ?label . " +
                                    "  FILTER(langMatches(lang(?label), 'EN')) " +
                                    "} LIMIT 3";

                    Query labelQuery = QueryFactory.create(labelQueryStr);
                    try (QueryExecution labelExec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, labelQuery)) {
                        ResultSet labelRs = labelExec.execSelect();
                        if (labelRs.hasNext()) {
                            QuerySolution labelSol = labelRs.next();
                            String label = labelSol.getLiteral("label").getString();
                            value = label + "|" + value;
                        }
                    }
                }
                value = value.contains("@") ? value.substring(0,value.lastIndexOf("@")) : value;
                System.out.println(readableLabel + " --> " + value);
                result.put(readableLabel, value);
            }
        }
        List<String> alternatives = getAlternativeLabels(uri);
        List<String> synonyms = getSynonyms(uri);
        List<String> superclassChain = getSuperclassChainFromUri(uri);

        if (!synonyms.isEmpty()) {
            result.put("Synonyms", String.join("; ", synonyms));
        }else{
            result.put("Synonyms", String.format("There are no synonyms for this searched term."));
        }

        if(!alternatives.isEmpty()){
            result.put("Alternative Labels", String.join("; ",alternatives));
        }else{
            result.put("Alternative Labels", String.format("There are no alternatives found.") );
        }

        if (!superclassChain.isEmpty()) {
            result.put("Superclass Chain", String.join(";", superclassChain));
        }


        System.out.println(result.get("Synonyms").toString());
        System.out.println(result.get("Alternative Labels").toString());
        return result;
    }
}