package vampus;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import jade.core.Runtime;

public class Launcher {
    public static void main(String[] args) {
        // Отримуємо JADE runtime
        Runtime runtime = Runtime.instance();

        // Створюємо профіль контейнера
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.GUI, "true"); // Відкриваємо графічний інтерфейс JADE

        // Створюємо контейнер агента
        ContainerController container = runtime.createMainContainer(profile);

        try {
            // Запускаємо агента Environment з ім'ям "environment"
            AgentController environmentAgent = container.createNewAgent("environment", "vampus.Environment", null);
            environmentAgent.start();

            // Запускаємо інших агентів, наприклад Agent1 та Agent2
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
