package wumpus;

import java.util.*;

public class Dictionary {

    private static final Map<String, List<String>> synonyms = new HashMap<>();
    private static final Random random = new Random();

    static {
        synonyms.put("Breeze", Arrays.asList(
                "A soft breeze touches my face",
                "The air feels cold and uneasy",
                "Something is not right around here",
                "I sense danger nearby"
        ));
        synonyms.put("Pit", Arrays.asList(
                "I feel like I'm falling!",
                "The ground just vanished beneath me",
                "It's a bottomless drop!",
                "This must be the end..."
        ));
        synonyms.put("Stench", Arrays.asList(
                "A foul smell fills the air",
                "It's hard to breathe here",
                "Something rotten is close",
                "The stench is unbearable!"
        ));
        synonyms.put("Wumpus", Arrays.asList(
                "I see glowing eyes in the dark",
                "The beast is right in front of me!",
                "My heart skips a beat",
                "I must act now or die!"
        ));
        synonyms.put("Wumpus death", Arrays.asList(
                "A loud scream echoes and fades",
                "The stench is suddenly gone",
                "I feel a wave of relief",
                "The Wumpus is no more"
        ));
    }

    public static String getRandomPhrase(String key) {
        List<String> options = synonyms.get(key);
        if (options == null || options.isEmpty()) return "";
        return options.get(random.nextInt(options.size()));
    }
}

