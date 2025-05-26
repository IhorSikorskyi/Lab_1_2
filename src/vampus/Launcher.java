package vampus;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import jade.core.Runtime;

public class Launcher {
    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();

        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "true"); // Відкриваємо графічний інтерфейс JADE

        ContainerController container = runtime.createMainContainer(profile);

        try {
            AgentController environmentAgent = container.createNewAgent("environment", "vampus.Environment", null);
            environmentAgent.start();

            AgentController navigator = container.createNewAgent("navigator", "vampus.Navigator", null);
            navigator.start();

            AgentController speleologist = container.createNewAgent("speleologist", "vampus.Speleologist", null);
            speleologist.start();

            System.out.println("Всі агенти запущені.");
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
}
