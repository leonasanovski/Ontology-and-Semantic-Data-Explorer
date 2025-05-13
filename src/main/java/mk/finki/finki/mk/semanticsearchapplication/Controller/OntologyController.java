package mk.finki.finki.mk.semanticsearchapplication.Controller;

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

    @GetMapping("/")
    public String index() {
        return "index";
    }

    //get the searched term and gives the related things with it
    @GetMapping("/search")
    public String search(@RequestParam String term, Model model) {
        List<Map<String,String>> results = ontologyService.queryOntology(term);
        model.addAttribute("results", results);
        model.addAttribute("term", term);
        long matches = results.stream()
                .map(r -> r.get("uri"))
                .distinct()
                .count();
        model.addAttribute("matches_count", matches);
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
