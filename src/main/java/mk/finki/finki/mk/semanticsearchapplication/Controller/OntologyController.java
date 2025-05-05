package mk.finki.finki.mk.semanticsearchapplication.Controller;



import mk.finki.finki.mk.semanticsearchapplication.Service.OntologyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class OntologyController {

    @Autowired
    private OntologyService ontologyService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam String term, Model model) {
        List<String> results = ontologyService.queryOntology(term);
        model.addAttribute("results", results);
        model.addAttribute("term", term);
        return "index";
    }
}
