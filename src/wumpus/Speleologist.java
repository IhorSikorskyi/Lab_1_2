package wumpus;

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
import java.util.Random;

public class Speleologist extends Agent {

    private AID navigatorAgent = null;
    private AID environmentAgent = null;

    private int posX = 0;
    private int posY = 0;
    private int mapSize = 4;
    private int arrow = 1;

    private Set<String> deadCells = new HashSet<>();
    private Random random = new Random();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started.");

        registerInDF();

        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                navigatorAgent = new AID("navigator", AID.ISLOCALNAME);
                environmentAgent = new AID("environment", AID.ISLOCALNAME);

                System.out.println("Speleologist: Navigator set to " + navigatorAgent.getLocalName());
                System.out.println("Speleologist: Environment set to " + environmentAgent.getLocalName());
            }
        });

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

                        case "shoot-request":
                            shoot(msg);
                            break;

                        case "take-gold":
                            handleGrab(msg);
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
        String content = msg.getContent();
        int newX = posX;
        int newY = posY;

        switch (content.toUpperCase()) {
            case "DOWN":
                newY += 1;
                break;
            case "UP":
                newY -= 1;
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

        if (newX < 0 || newY < 0 || newX >= mapSize || newY >= mapSize) {
            System.out.println("Speleologist: Move out of bounds attempted: (" + newX + "," + newY + ")");
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("move-response");
            reply.setContent("Move out of bounds");
            send(reply);
            return;
        }

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

        ACLMessage envReq = new ACLMessage(ACLMessage.REQUEST);
        envReq.addReceiver(environmentAgent);
        envReq.setConversationId("env-request");
        envReq.setContent(queryX + "," + queryY);
        send(envReq);

        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(environmentAgent),
                MessageTemplate.MatchConversationId("env-request"));

        ACLMessage envResp = blockingReceive(mt, 3000);

        ACLMessage reply = msg.createReply();

        if (envResp != null) {
            String envContent = envResp.getContent(); // наприклад: "draft,stench"
            String responseMessage = createEnvMessage(envContent);

            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("env-request");
            reply.setContent(responseMessage);

            System.out.println("Speleologist: " + responseMessage);
        } else {
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("env-request");
            reply.setContent("No response from Environment");
            System.out.println("Speleologist: No response from Environment.");
        }
        send(reply);
    }

    private void handleGrab(ACLMessage msg) {
        if (environmentAgent == null) {
            System.out.println("Speleologist: Environment agent not available for take-gold.");
            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setConversationId("take-gold");
            reply.setContent("Environment agent not found");
            send(reply);
            return;
        }

        ACLMessage GrabMsg = new ACLMessage(ACLMessage.REQUEST);
        GrabMsg.addReceiver(environmentAgent);
        GrabMsg.setConversationId("take-gold");
        GrabMsg.setContent(posX + "," + posY);

        send(GrabMsg);

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

    private void handleResetPath(ACLMessage msg) {
        resetState();
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("reset-path");
        reply.setContent("Path reset");
        send(reply);
        System.out.println("Speleologist: Path reset on command.");
    }

    private void handleDeadCells(ACLMessage msg) {
        String content = msg.getContent();
        deadCells.clear();
        if (!content.isEmpty()) {
            String[] cells = content.split(";");
            for (String cell : cells) {
                deadCells.add(cell.trim());
            }
        }
        System.out.println("Speleologist: Dead cells updated: " + deadCells);
    }

    public void resetState() {
        posX = 0;
        posY = 0;
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

    public void shoot(ACLMessage msg) {
        if (msg != null) {
            if (arrow <= 0) {
                System.out.println("Speleologist: No arrows left to shoot.");

                // Send failure reply to Navigator
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setConversationId("shoot-request");
                reply.setContent("No arrows left");
                send(reply);
                return;
            }

            arrow--;
            System.out.println("Speleologist: Trying to kill Wumpus...");

            boolean hit = random.nextBoolean();

            if (hit) {
                System.out.println("Speleologist: Wumpus hit! Sending death notification to Environment.");

                // Notify Environment about Wumpus death
                ACLMessage msg_t = new ACLMessage(ACLMessage.INFORM);
                msg_t.addReceiver(environmentAgent);
                msg_t.setConversationId("Wumpus");
                msg_t.setContent(posX + "," + posY);
                send(msg_t);

                System.out.println("Speleologist: Wumpus hit at (" + posX + "," + posY + ")");

                // Send success reply to Navigator
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setConversationId("shoot-request");
                reply.setContent("Killed");
                send(reply);

                String responseMessage = createEnvMessage("Wumpus death");
                System.out.println(responseMessage);
            } else {
                System.out.println("Speleologist: Missed the shot! Speleologist died.");

                // Send failure reply to Navigator before dying
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.FAILURE);
                reply.setConversationId("shoot-request");
                reply.setContent("Missed shot - died");
                send(reply);

                resetState();

                // Notify Environment about speleologist death
                ACLMessage deathMsg = new ACLMessage(ACLMessage.INFORM);
                deathMsg.addReceiver(environmentAgent);
                deathMsg.setConversationId("speleologist-dead");
                deathMsg.setContent("Speleologist died by missing Wumpus shot.");
                send(deathMsg);
            }
        }
    }

    private String createEnvMessage(String envResponse) {
        String[] lines = envResponse.split("\n");
        String statesLine = null;

        for (String line : lines) {
            if (line.toLowerCase().startsWith("states:")) {
                statesLine = line.substring(7).trim();  // Вирізаємо "States:" і пробіли
                break;
            }
        }

        if (statesLine == null || statesLine.isEmpty()) {
            return "No environmental signs detected.";
        }

        String[] signs = statesLine.split(",");
        StringBuilder message = new StringBuilder();

        for (String sign : signs) {
            sign = sign.trim().toLowerCase();
            String capitalizedSign = capitalizeFirstLetter(sign);
            String phrase = Dictionary.getRandomPhrase(capitalizedSign);
            if (!phrase.isEmpty()) {
                if (message.length() > 0) {
                    message.append(". ");
                }
                message.append(phrase);
            }
        }

        return message.toString();
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}