package mk.finki.finki.mk.semanticsearchapplication.Component;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SearchTermTracker {
    private final Map<String, Integer> searches = new HashMap<>();

    public void trackSearch(String searchedTerm){
        searches.merge(searchedTerm.toLowerCase(), 1, Integer::sum);
    }

    public List<String> getTopNSearchedTerms(int n){
        return searches.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .toList();
    }
}
