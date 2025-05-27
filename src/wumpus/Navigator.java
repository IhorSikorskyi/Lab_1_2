package wumpus;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

public class Navigator extends Agent {
    private List<Position> currentAgentPosition = new ArrayList<>();
    private Set<Position> visitedCells = new HashSet<>();
    private Set<Position> deadCells = new HashSet<>();
    private AID[] speleologistAgent;
    private String environmentState = "";
    private int mapSize = 4;
    private int directionIndex = 0;

    @Override
    protected void setup() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("navigator");
        sd.setName(getLocalName() + "navigator");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + ": Registered in DF as navigator.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        speleologistAgent = new AID[] { new AID("speleologist", AID.ISLOCALNAME) };
        currentAgentPosition.add(new Position(0, 0));

        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                onStep();
            }
        });

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive(MessageTemplate.MatchConversationId("terminate"));
                if (msg != null) {
                    System.out.println(getLocalName() + ": отримано команду завершення.");
                    doDelete();
                } else {
                    block();
                }
            }
        });
    }

    protected void onStep() {
        System.out.println(getLocalName() + ": onStep called");

        Position currentPos = currentAgentPosition.get(currentAgentPosition.size() - 1);
        visitedCells.add(currentPos);

        // Only check for Gold here - dangers are handled in moveTo()
        if (environmentState.contains("Gold")) {
            System.out.println("Gold found!");
            takeGold();
            stepBack();
            return;
        }

        Position next = getNextSpiralMove(currentPos);
        if (next != null) {
            moveTo(next);
        } else {
            System.out.println("Exploration complete.");
            doDelete();
        }
    }

    private void sendDeadCellsToSpeleologist() {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(speleologistAgent[0]);
        msg.setConversationId("dead-cells");
        StringBuilder sb = new StringBuilder();
        for (Position p : deadCells) {
            sb.append(p.x).append(",").append(p.y).append(";");
        }
        if (sb.length() > 0) sb.setLength(sb.length() - 1);

        msg.setContent(sb.toString());
        send(msg);
        System.out.println("Navigator: Sent dead cells to Speleologist: " + sb.toString());
    }

    private Position getNextSpiralMove(Position current) {
        int[][] directions = {
                {1, 0},
                {0, 1},
                {-1, 0},
                {0, -1}
        };

        Position prevPos = null;
        if (currentAgentPosition.size() >= 2) {
            prevPos = currentAgentPosition.get(currentAgentPosition.size() - 2);
        }

        java.util.function.Predicate<Position> isValidMove = pos ->
                pos.x >= 0 && pos.y >= 0 && pos.x < mapSize && pos.y < mapSize
                        && !visitedCells.contains(pos)
                        && !deadCells.contains(pos);

        if (prevPos != null && isValidMove.test(prevPos)) {
            System.out.println("Trying to move BACK to: (" + prevPos.x + "," + prevPos.y + ")");
            return prevPos;
        }

        for (int i = 0; i < 4; i++) {
            int dirIndex = (directionIndex + i) % 4;
//            int nx = current.x + directions[dirIndex][0];
//            int ny = current.y + directions[dirIndex][1];
             int nx = current.x + directions[dirIndex][1];
             int ny = current.y + directions[dirIndex][0];
            Position nextPos = new Position(nx, ny);

            System.out.println("Trying direction " + dirIndex + ": (" + nx + "," + ny + ")");
            if (isValidMove.test(nextPos)) {
                directionIndex = dirIndex;
                System.out.println("Next move selected: (" + nx + "," + ny + ")");
                return nextPos;
            }
        }
        return null;
    }

    // In Navigator.java - replace the moveTo() method with this fixed version:

    private void moveTo(Position target) {
        Position current = currentAgentPosition.get(currentAgentPosition.size() - 1);

        String direction = null;
        if (target.x == current.x + 1 && target.y == current.y) direction = "RIGHT";
        else if (target.x == current.x - 1 && target.y == current.y) direction = "LEFT";
        else if (target.x == current.x && target.y == current.y - 1) direction = "UP";
        else if (target.x == current.x && target.y == current.y + 1) direction = "DOWN";
        else direction = "INVALID";

        if (direction != null && !direction.equals("INVALID")) {
            boolean moveSuccess = sendMoveRequest(direction);

            if (!moveSuccess) {
                System.out.println("Navigator: Move to " + direction + " failed. Marking as dead cell.");
                deadCells.add(target);
                return;
            }

            Position newPos = getPositionFromSpeleologist();

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(speleologistAgent[0]);
            msg.setConversationId("env-request");
            msg.setContent(newPos.x + "," + newPos.y);
            send(msg);

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("env-request")
            );

            ACLMessage reply = blockingReceive(mt, 3000);
            String newEnvState = "";
            if (reply != null) {
                newEnvState = reply.getContent();
                System.out.println("Navigator: Env state at new position (" + newPos.x + "," + newPos.y + "): " + newEnvState);
            } else {
                System.out.println("No environment response at new position.");
            }

            // FIXED: Check for Wumpus in the environment message
            // The Speleologist converts "Wumpus" to phrases like "I see glowing eyes in the dark"
            if (newEnvState.toLowerCase().contains("glowing eyes") ||
                    newEnvState.toLowerCase().contains("wumpus") ||
                    newEnvState.toLowerCase().contains("dark eyes") ||
                    newEnvState.toLowerCase().contains("terrible smell")) {

                System.out.println("Navigator: Wumpus detected! Attempting to shoot...");

                ACLMessage shootMsg = new ACLMessage(ACLMessage.REQUEST);
                shootMsg.setConversationId("shoot-request");
                shootMsg.setContent("shoot-request");
                shootMsg.addReceiver(speleologistAgent[0]);
                send(shootMsg);

                System.out.println("Navigator: Shoot message sent to Speleologist.");

                ACLMessage shootReply = blockingReceive(
                        MessageTemplate.MatchConversationId("shoot-request"), 10000);

                if (shootReply == null) {
                    System.out.println("Navigator: No shoot response - assuming death. Restarting...");
                    deadCells.add(newPos);
                    sendDeadCellsToSpeleologist();
                    restart();
                    return;
                }

                System.out.println("Navigator: Shoot response: " + shootReply.getContent());

                if (shootReply.getPerformative() == ACLMessage.FAILURE) {
                    System.out.println("Navigator: Shot failed - Speleologist died. Restarting...");
                    deadCells.add(newPos);
                    sendDeadCellsToSpeleologist();
                    restart();
                    return;
                }

                System.out.println("Navigator: Shot successful! Refreshing environment...");
                // Refresh environment state after successful shot
                ACLMessage refresh = new ACLMessage(ACLMessage.REQUEST);
                refresh.addReceiver(speleologistAgent[0]);
                refresh.setConversationId("env-request");
                refresh.setContent(newPos.x + "," + newPos.y);
                send(refresh);

                ACLMessage freshReply = blockingReceive(MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchConversationId("env-request")
                ), 3000);

                if (freshReply != null) {
                    newEnvState = freshReply.getContent();
                    System.out.println("Navigator: Refreshed env state: " + newEnvState);
                } else {
                    newEnvState = "";
                }
            }

            // Check for Pit after Wumpus handling
            if (newEnvState.toLowerCase().contains("pit") ||
                    newEnvState.toLowerCase().contains("wind") ||
                    newEnvState.toLowerCase().contains("breeze")) {
                System.out.println("Navigator: Pit detected! Died at: (" + newPos.x + ", " + newPos.y + ")");
                deadCells.add(newPos);
                sendDeadCellsToSpeleologist();
                restart();
                return;
            }

            // If we reach here, the move was successful
            currentAgentPosition.add(newPos);
            environmentState = newEnvState;
            System.out.println("Navigator: Step " + direction + " to (" + newPos.x + ", " + newPos.y + ")");

        } else {
            System.out.println("Navigator: Invalid move direction.");
        }
    }

    private void restart() {
        System.out.println("Restarting after death...");
        currentAgentPosition.clear();
        currentAgentPosition.add(new Position(0, 0));
        visitedCells.clear();
        environmentState = "";
        directionIndex = 0;

        ACLMessage resetMsg = new ACLMessage(ACLMessage.REQUEST);
        resetMsg.addReceiver(speleologistAgent[0]);
        resetMsg.setConversationId("reset-path");
        resetMsg.setContent("reset");
        send(resetMsg);
    }

    private void takeGold() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(speleologistAgent[0]);
        msg.setConversationId("take-gold");
        msg.setContent("TakeGold");
        send(msg);
    }

    private boolean sendMoveRequest(String direction) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(speleologistAgent[0]);
        msg.setConversationId("move-request");
        msg.setContent(direction);
        send(msg);

        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId("move-response")
        );
        ACLMessage reply = blockingReceive(mt, 3000);
        if (reply != null) {
            System.out.println("Move confirmed: " + reply.getContent());
            String content = reply.getContent();
            if (content.matches("\\d+,\\d+")) {
                return true;
            } else {
                return content.equalsIgnoreCase("OK");
            }
        } else {
            System.out.println("Move confirmation not received.");
            return false;
        }
    }

    private Position getPositionFromSpeleologist() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setConversationId("get-position");
        msg.setContent("RequestPosition");
        msg.addReceiver(speleologistAgent[0]);
        send(msg);

        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchConversationId("get-position")
        );

        ACLMessage reply = blockingReceive(mt, 3000);
        if (reply != null) {
            String[] parts = reply.getContent().split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);

            if (x < 0 || y < 0 || x >= mapSize || y >= mapSize) {
                return currentAgentPosition.get(currentAgentPosition.size() - 1);
            }
            return new Position(x, y);
        }
        return currentAgentPosition.get(currentAgentPosition.size() - 1);
    }

    private void stepBack() {
        System.out.println("Navigator: Returning to (0,0)...");

        for (int i = currentAgentPosition.size() - 1; i > 0; i--) {
            Position from = currentAgentPosition.get(i);
            Position to = currentAgentPosition.get(i - 1);
            String revDir = getReverseDirection(from, to);
            sendMoveRequest(revDir);
            System.out.println("Step back: " + revDir);
        }

        currentAgentPosition.clear();
        currentAgentPosition.add(new Position(0, 0));
        broadcastTerminate();
    }

    static class Position {
        int x;
        int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Position)) return false;
            Position pos = (Position) obj;
            return x == pos.x && y == pos.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    private void broadcastTerminate() {
        ACLMessage terminateMsg = new ACLMessage(ACLMessage.INFORM);
        terminateMsg.setConversationId("terminate");
        terminateMsg.setContent("Shutdown");

        terminateMsg.addReceiver(getAID());

        for (AID aid : speleologistAgent) {
            terminateMsg.addReceiver(aid);
        }

        send(terminateMsg);
    }
    private String getReverseDirection(Position from, Position to) {
        if (to.x < from.x && to.y == from.y) return "LEFT";
        if (to.x > from.x && to.y == from.y) return "RIGHT";
        if (to.y < from.y && to.x == from.x) return "UP";
        if (to.y > from.y && to.x == from.x) return "DOWN";
        return "";
    }
}