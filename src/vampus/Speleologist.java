package vampus;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.DFService;
import jade.domain.FIPAException;

import java.util.HashSet;
import java.util.Set;

public class Speleologist extends Agent {

    private AID navigatorAgent = null;
    private AID environmentAgent = null;

    private int posX = 0;
    private int posY = 0;
    private int mapSize = 4;

    // Множина точок смерті, отримана від Navigator
    private Set<String> deadCells = new HashSet<>();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        // Реєстрація в DF як speleologist-service
        registerInDF();

        // Знайти Navigator та Environment
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                navigatorAgent = new AID("navigator", AID.ISLOCALNAME);
                environmentAgent = new AID("environment", AID.ISLOCALNAME);

                System.out.println("Speleologist: Navigator set to " + navigatorAgent.getLocalName());
                System.out.println("Speleologist: Environment set to " + environmentAgent.getLocalName());
            }
        });

        // Основна поведінка — обробка запитів від Navigator
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                ACLMessage msg = myAgent.receive(mt);

                if (msg != null) {
                    String convId = msg.getConversationId();
                    String content = msg.getContent();

                    switch (convId) {
                        case "move-request":
                            handleMoveRequest(msg);
                            break;

                        case "get-position":
                            handleGetPositionRequest(msg);
                            break;

                        case "env-request":
                            handleEnvRequest(msg);
                            break;

                        case "take-gold":
                            handleTakeGold(msg);
                            break;

                        case "reset-path":
                            handleResetPath(msg);
                            break;

                        case "dead-cells":
                            handleDeadCells(msg);
                            break;

                        default:
                            System.out.println("Speleologist: Unknown conversationId: " + convId);
                            break;
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());

        ServiceDescription sd = new ServiceDescription();
        sd.setType("speleologist");
        sd.setName(getLocalName() + "speleologist");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": Registered in DF as speleologist.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private void handleMoveRequest(ACLMessage msg) {
        String content = msg.getContent(); // "UP", "DOWN", "LEFT", "RIGHT"
        int newX = posX;
        int newY = posY;

        switch (content.toUpperCase()) {
            case "DOWN":
                newY += 1;  // вниз — збільшуємо Y
                break;
            case "UP":
                newY -= 1;  // вверх — зменшуємо Y
                break;
            case "LEFT":
                newX -= 1;
                break;
            case "RIGHT":
                newX += 1;
                break;
            default:
                System.out.println("Speleologist: Unknown move direction: " + content);
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setConversationId("move-response");
                reply.setContent("Unknown move direction");
                send(reply);
                return;
        }

        // Перевірка кордонів карти
        if (newX < 0 || newY < 0 || newX >= mapSize || newY >= mapSize) {
            System.out.println("Speleologist: Move out of bounds attempted: (" + newX + "," + newY + ")");
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("move-response");
            reply.setContent("Move out of bounds");
            send(reply);
            return;
        }

        // Перевірка, чи не є нова позиція "мертвою клітинкою"
        String cellKey = newX + "," + newY;
        if (deadCells.contains(cellKey)) {
            System.out.println("Speleologist: Attempted move to dead cell: " + cellKey);
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("move-response");
            reply.setContent("Move to dead cell blocked");
            send(reply);
            return;
        }

        posX = newX;
        posY = newY;

        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("move-response");
        reply.setContent(posX + "," + posY);
        System.out.println("Speleologist: Moved " + content + " to position (" + posX + "," + posY + ")");
        send(reply);
    }

    private void handleGetPositionRequest(ACLMessage msg) {
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("get-position");
        reply.setContent(posX + "," + posY);
        send(reply);

        System.out.println("Speleologist: Sent position (" + posX + "," + posY + ")");
    }

    private void handleEnvRequest(ACLMessage msg) {
        if (environmentAgent == null) {
            System.out.println("Speleologist: Environment agent not available.");
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("env-request");
            reply.setContent("Environment agent not found");
            send(reply);
            return;
        }

        // Розбираємо координати із запиту (наприклад, "x,y")
        String content = msg.getContent();
        String[] parts = content.split(",");
        if (parts.length != 2) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("env-request");
            reply.setContent("Invalid coordinates format");
            send(reply);
            return;
        }

        int queryX, queryY;
        try {
            queryX = Integer.parseInt(parts[0]);
            queryY = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("env-request");
            reply.setContent("Invalid coordinate numbers");
            send(reply);
            return;
        }

        // Надіслати запит агенту Environment з координатами
        ACLMessage envReq = new ACLMessage(ACLMessage.REQUEST);
        envReq.addReceiver(environmentAgent);
        envReq.setConversationId("env-request");
        envReq.setContent(queryX + "," + queryY);
        send(envReq);

        // Очікуємо відповідь від Environment
        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(environmentAgent),
                MessageTemplate.MatchConversationId("env-request"));

        ACLMessage envResp = blockingReceive(mt, 3000);

        ACLMessage reply = msg.createReply();

        if (envResp != null) {
            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("env-request");
            reply.setContent(envResp.getContent());
            System.out.println("Speleologist: Env state at (" + queryX + "," + queryY + "): " + envResp.getContent());
        } else {
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("env-request");
            reply.setContent("No response from Environment");
            System.out.println("Speleologist: No response from Environment.");
        }

        send(reply);
    }

    private void handleTakeGold(ACLMessage msg) {
        if (environmentAgent == null) {
            System.out.println("Speleologist: Environment agent not available for take-gold.");
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("take-gold");
            reply.setContent("Environment agent not found");
            send(reply);
            return;
        }

        ACLMessage takeGoldMsg = new ACLMessage(ACLMessage.REQUEST);
        takeGoldMsg.addReceiver(environmentAgent);
        takeGoldMsg.setConversationId("take-gold");
        takeGoldMsg.setContent(posX + "," + posY);

        send(takeGoldMsg);

        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(environmentAgent),
                MessageTemplate.MatchConversationId("take-gold-response"));

        ACLMessage response = blockingReceive(mt, 3000);

        ACLMessage reply = msg.createReply();

        if (response != null) {
            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("take-gold");
            reply.setContent(response.getContent());
            System.out.println("Speleologist: take-gold response: " + response.getContent());
        } else {
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("take-gold");
            reply.setContent("No response from Environment");
            System.out.println("Speleologist: No response from Environment on take-gold.");
        }

        send(reply);
    }

    // Обробка команди скинути шлях (наприклад, після смерті)
    private void handleResetPath(ACLMessage msg) {
        resetState();
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("reset-path");
        reply.setContent("Path reset");
        send(reply);
        System.out.println("Speleologist: Path reset on command.");
    }

    // Обробка повідомлення з оновленням deadCells від Navigator
    private void handleDeadCells(ACLMessage msg) {
        String content = msg.getContent(); // формат: "x1,y1;x2,y2;..."
        deadCells.clear();
        if (!content.isEmpty()) {
            String[] cells = content.split(";");
            for (String cell : cells) {
                deadCells.add(cell.trim());
            }
        }
        System.out.println("Speleologist: Dead cells updated: " + deadCells);
    }

    // Метод для скидання внутрішнього стану (позиції і deadCells)
    public void resetState() {
        posX = 0;
        posY = 0;
        deadCells.clear();  // якщо треба, можна не чистити, залежно від логіки
        System.out.println("Speleologist: State reset after death.");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " terminating.");
    }
}