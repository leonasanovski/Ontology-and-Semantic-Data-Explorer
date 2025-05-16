package mk.finki.finki.mk.semanticsearchapplication.Web;

import mk.finki.finki.mk.semanticsearchapplication.Component.SearchTermTracker;
import mk.finki.finki.mk.semanticsearchapplication.Service.OntologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class OntologyController {

    @Autowired
    private OntologyService ontologyService;
    @Autowired
    private SearchTermTracker searchTermTracker;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam String term, Model model) {
        if (term == null || term.trim().isEmpty() || !term.matches("^[\\p{L}0-9\\s\\-_,.()]+$")) {
            model.addAttribute("error", String.format("The term %s you searched, does not exist. Try again :)", term));
            return "index";
        }
        List<Map<String,String>> results = ontologyService.queryOntology(term);
        searchTermTracker.trackSearch(term);

        model.addAttribute("results", results);
        model.addAttribute("term", term);
        long matches = results.stream()
                .map(r -> r.get("uri"))
                .distinct()
                .count();
        model.addAttribute("matches_count", matches);
        model.addAttribute("top_searches", searchTermTracker.getTopNSearchedTerms(3));
        return "index";
    }

    @GetMapping("/details")
    public String getClassDetails(@RequestParam String uri, Model model) {
        Map<String, String> details = ontologyService.getClassDetails(uri);
        model.addAttribute("details", details);
        model.addAttribute("uri", uri);
        return "details";
    }
    @GetMapping("/api/tree/roots")
    @ResponseBody
    public List<Map<String, Object>> getRootTree() {
        return ontologyService.getChildrenForUri(null, null);
    }

    @GetMapping("/api/tree/children")
    @ResponseBody
    public List<Map<String, Object>> getChildren(
            @RequestParam String uri,
            @RequestParam(required = false) String selectedUri) {
        return ontologyService.getChildrenForUri(uri, selectedUri);
    }
    @GetMapping("/api/tree/path")
    @ResponseBody
    public List<String> getPath(@RequestParam String uri) {
        return ontologyService.getPathToRoot(uri);
    }
}
