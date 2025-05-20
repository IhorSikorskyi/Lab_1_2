package booktrading;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class Launcher {
    public static void main(String[] args) {
        try {
            // Запуск JADE контейнера
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.GUI, "true");  // Запускає JADE з GUI

            AgentContainer container = rt.createMainContainer(p);

            // Запуск Seller агентів
            AgentController seller1 = container.createNewAgent(
                    "seller1",
                    "booktrading.BookSellerAgent",
                    new Object[]{"Harry Potter", "650", "Lord of The Ring: Middle Earth", "800"}
            );
            seller1.start();

            AgentController seller2 = container.createNewAgent(
                    "seller2",
                    "booktrading.BookSellerAgent",
                    new Object[]{"Cyberpunk 2035", "575", "The War", "425"}
            );
            seller2.start();

            // Запуск Buyer агента з аргументом назви книги
            Object[] buyerArgs = new Object[]{"Lord of The Ring: Middle Earth"};
            AgentController buyer = container.createNewAgent("buyer1", "booktrading.BookBuyerAgent", buyerArgs);
            buyer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
