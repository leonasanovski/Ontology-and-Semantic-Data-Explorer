package mk.finki.finki.mk.semanticsearchapplication.Service;


import org.apache.jena.query.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OntologyService {

    private static final String FUSEKI_ENDPOINT = "http://localhost:3030/food_DB/query";

    public List<String> queryOntology(String searchTerm) {
        List<String> results = new ArrayList<>();

        String sparql = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
                "SELECT ?label WHERE { " +
                "  ?s rdfs:label ?label . " +
                "  FILTER(CONTAINS(LCASE(str(?label)), '" + searchTerm.toLowerCase() + "')) " +
                "} LIMIT 20";

        Query query = QueryFactory.create(sparql);
        try (QueryExecution qexec = QueryExecutionFactory.sparqlService(FUSEKI_ENDPOINT, query)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                results.add(sol.get("label").toString());
            }
        }

        return results;
    }
}