package wumpus;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Environment extends Agent {
    private Map<String, Set<String>> environmentGrid;

    protected void setup() {
        System.out.println(getLocalName() + ": стартую.");

        buildEnvironment();
        fillEnvironment();

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("environment-agent");
        sd.setName("environment");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ":Registered in DF as 'environment'.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new HandleRequestsBehaviour());
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
            System.out.println(getLocalName() + ":deregistered DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + ":stoped.");
    }

    private void buildEnvironment() {
        environmentGrid = new HashMap<>();
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                environmentGrid.put(key(x, y), new HashSet<>());
            }
        }
        System.out.println(getLocalName() + ": environment is built (4x4).");
    }

    private void fillEnvironment() {
        addState(0, 0, "Normal");
        addState(1, 0, "Breeze");
        addState(2, 0, "Pit");
        addState(3, 0, "Breeze");

        addState(0, 1, "Stench");
        addState(1, 1, "Breeze");
        addState(2, 1, "Breeze");
        addState(3, 1, "Breeze");

        addState(0, 2, "Wumpus");
        addState(1, 2, "Breeze");
        addState(1, 2, "Stench");
        addState(1, 2, "Gold");
        addState(2, 2, "Pit");
        addState(3, 2, "Breeze");

        addState(0, 3, "Stench");
        addState(1, 3, "Breeze");
        addState(2, 3, "Breeze");
        addState(3, 3, "Pit");

        System.out.println(getLocalName() + ": the environment is filled with states.");
    }

    private void addState(int x, int y, String state) {
        String k = key(x, y);
        Set<String> states = environmentGrid.get(k);
        if (states != null) {
            states.add(state);
        }
    }

    private String key(int x, int y) {
        return x + "," + y;
    }

    private class HandleRequestsBehaviour extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                String content = msg.getContent();
                String convId = msg.getConversationId();

                ACLMessage reply = msg.createReply();

                switch (convId) {
                    case "env-request":
                        Set<String> states = environmentGrid.get(content);
                        reply.setPerformative(ACLMessage.INFORM);
                        if (states == null || states.isEmpty()) {
                            reply.setContent("Coordinates: " + content + "\nStates: Normal");
                        } else {
                            reply.setContent("Coordinates: " + content + "\nStates: " + String.join(",", states));
                        }
                        myAgent.send(reply);
                        System.out.println(getLocalName() + ":\nStates: [" + content + "] => " + reply.getContent());
                        break;
                    case "take-gold":
                        handleTakeGold(content, reply);
                        break;
                    case "Wumpus":
                        handleWumpusDeath(content, reply);
                        break;
                    default:
                        reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                        reply.setContent("Unknown conversation ID: " + convId);
                        myAgent.send(reply);
                        break;
                }
            } else {
                block();
            }
        }
    }

    private void handleTakeGold(String content, ACLMessage reply) {
        Set<String> states = environmentGrid.get(content);
        if (states != null && states.contains("Gold")) {
            states.remove("Gold");
            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("take-gold-response");
            reply.setContent("Gold taken at " + content);
            System.out.println(getLocalName() + ": Gold taken at " + content);
        } else {
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("take-gold-response");
            reply.setContent("No gold at " + content);
            System.out.println(getLocalName() + ": No gold to take at " + content);
        }
        send(reply);
    }

    private void handleWumpusDeath(String content, ACLMessage reply) {
        Set<String> states = environmentGrid.get(content);
        if (states != null && states.contains("Wumpus")) {
            states.remove("Wumpus");
            states.remove("Stench");

            String[] parts = content.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);

            int[][] directions = {
                    {-1, 0}, {1, 0}, // Ліворуч, праворуч
                    {0, -1}, {0, 1}  // Вгору, вниз
            };

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];

                String neighborKey = key(nx, ny);
                Set<String> neighborStates = environmentGrid.get(neighborKey);

                if (neighborStates != null) {
                    neighborStates.remove("Stench");
                }
            }

            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("wumpus-dead-response");
            reply.setContent("Wumpus dead at " + content);
            System.out.println(getLocalName() + ": Wumpus dead at " + content + ". Stench cleared.");
        } else {
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("wumpus-dead-response");
            reply.setContent("No wumpus at " + content);
            System.out.println(getLocalName() + ": No Wumpus at " + content);
        }
        send(reply);
    }

}