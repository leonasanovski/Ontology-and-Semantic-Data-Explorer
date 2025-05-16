package mk.finki.finki.mk.semanticsearchapplication.Service;

import mk.finki.finki.mk.semanticsearchapplication.Component.SearchTermTracker;
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
//    private final SearchTermTracker searchTermTracker;
    private static final String FUSEKI_ENDPOINT = "http://localhost:3030/food_DB/query";
    private static final Map<String, String> PROPERTY_LABELS = Map.ofEntries(
            Map.entry("http://www.w3.org/2000/01/rdf-schema#label", "Preferred Name"),
            Map.entry("http://www.w3.org/2000/01/rdf-schema#subClassOf", "Subclass Of"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000115", "Definition"),
            Map.entry("http://purl.obolibrary.org/obo/IAO_0000412", "Ontology Source"),
            Map.entry("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "Type")
    );
    private static final String HAS_CHILD_BOOLEAN = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        ASK {
            ?child rdfs:subClassOf <%s> .
        }
    """;
    private static final String SUPER_CLASS = """
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        SELECT ?super WHERE {
            <%s> rdfs:subClassOf ?super .
        } LIMIT 1
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
//
//    public OntologyService(SearchTermTracker searchTermTracker) {
//        this.searchTermTracker = searchTermTracker;
//    }

    public static String buildSearchQuery(String searchTerm) {
        return "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
                "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
                "SELECT ?class ?label WHERE { " +
                "  ?class rdf:type owl:Class . " +
                "  ?class rdfs:label ?label . " +
                "  FILTER(CONTAINS(LCASE(str(?label)), \"" + searchTerm.toLowerCase() + "\")) " +
                "} ORDER BY ?label";
    }
    public static String buildSuperChainQuery(String uri){
        return "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "SELECT DISTINCT ?superclass ?label WHERE { " +
                "  <" + uri + "> rdfs:subClassOf+ ?superclass . " +
                "  ?superclass rdfs:label ?label . " +
                "  FILTER(langMatches(lang(?label), 'EN')) } ORDER BY ?label";
    }
    public static String directSuperclassQuery(String currentUri){
        return "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "SELECT ?superclass ?label WHERE { " +
                "  <" + currentUri + "> rdfs:subClassOf ?superclass . " +
                "  OPTIONAL { ?superclass rdfs:label ?label . FILTER(langMatches(lang(?label), \"EN\")) } " +
                "} LIMIT 1";
    }
    public static String synonymsQuery(String uri){
        return "PREFIX obo: <http://purl.obolibrary.org/obo/> " +
                "SELECT ?synonym WHERE { " +
                "<" + uri + "> obo:IAO_0000111 ?synonym . " +
                "FILTER(langMatches(lang(?synonym), 'EN')) }";
    }
    public static String attributeQuery(String uri){
        return "PREFIX skos: <http://www.w3.org/2004/02/skos/core#> " +
                "SELECT ?alt WHERE { " +
                "<" + uri + "> skos:altLabel ?alt . " +
                "FILTER(langMatches(lang(?alt), 'EN')) }";
    }
    public static String getPropertiesQuery(String uri){
        return "SELECT ?prop ?value WHERE { " +
                "<" + uri + "> ?prop ?value " +
                "}";
    }

    public static String labelsInRdf(String value){
        return "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
                "SELECT ?label WHERE { " +
                "  OPTIONAL { <" + value + "> a owl:Class . } " +
                "  <" + value + "> rdfs:label ?label . " +
                "  FILTER(langMatches(lang(?label), 'EN')) " +
                "} LIMIT 3";
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
            path.add(0, uri);  // add to front so root is first
            uri = getSuperclassOf(uri);
        }
        return path;
    }

    private String getSuperclassOf(String uri) {
        String queryStr = String.format(SUPER_CLASS, uri);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, QueryFactory.create(queryStr))) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                return results.next().getResource("super").getURI();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public List<Map<String, Object>> getChildrenForUri(String parentUri, String selectedUri){
        String queryStr;

        if (parentUri == null) {
            queryStr = TOP_LEVEL_CLASSES;
        } else {
            queryStr = String.format(DIRECT_SUBCLASSES, parentUri);
        }

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
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }



    private List<String> getSuperclassChain(String uri) {
        List<String> chain = new ArrayList<>();

        String queryStr = buildSuperChainQuery(uri);

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
//        searchTermTracker.trackSearch(searchTerm);

        List<Map<String, String>> results = new ArrayList<>();

        String sparql = buildSearchQuery(searchTerm);

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
        String queryStr = directSuperclassQuery(currentUri);
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

        String synonymQueryStr = synonymsQuery(uri);

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
        String queryStr = attributeQuery(uri);

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
    private String getLabelForUri(String uri) {
        String queryStr = labelsInRdf(uri);
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
    public Map<String, String> getClassDetails(String uri) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Id", uri);

        String sparql = getPropertiesQuery(uri);

        Query query = QueryFactory.create(sparql);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String rawProp = sol.get("prop").toString();
                String value = sol.get("value").toString();
                String readableLabel = PROPERTY_LABELS.getOrDefault(rawProp, rawProp);
                if (value.startsWith("http")) {
                    String label = getLabelForUri(value);
                    if (label != null) {
                        value = label + "|" + value;
                    }
                }
                value = value.contains("@") ? value.substring(0,value.lastIndexOf("@")) : value;
                System.out.println(readableLabel + " --> " + value);
                result.put(readableLabel, value);
            }
        }
        String[] segments = uri.split("[/#]");
        String notation = segments[segments.length - 1];
        result.put("Notation", notation);

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