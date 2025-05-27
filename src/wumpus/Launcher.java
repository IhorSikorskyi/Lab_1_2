package wumpus;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.core.Runtime;

public class Launcher {
    public static void main(String[] args) {
        try {
            Runtime rt = Runtime.instance();
            Profile mainProfile = new ProfileImpl();
            mainProfile.setParameter(Profile.MAIN_HOST, "localhost");
            mainProfile.setParameter(Profile.MAIN, "true");  // Головний контейнер

            AgentContainer mainContainer = rt.createMainContainer(mainProfile);

            AgentController environmentAgent = mainContainer.createNewAgent("environment", "wumpus.Environment", null);
            environmentAgent.start();

            AgentController navigator = mainContainer.createNewAgent("navigator", "wumpus.Navigator", null);
            navigator.start();

            AgentController speleologist = mainContainer.createNewAgent("speleologist", "wumpus.Speleologist", null);
            speleologist.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
