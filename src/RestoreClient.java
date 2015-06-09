import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

/**
 * VSS
 * Created by Admiral Helmut on 20.05.2015.
 */
public class RestoreClient {

    private static int allSeats;
    private static int allPhilosopher;
    private static int allHungryPhilosopher;
    private static int eatTime;
    private static int meditationTime;
    private static int sleepTime;
    private static long endTime;

    private static String leftneighbourIP;
    private static String leftneighbourLookupName;
    private static String rightneighbourIP;
    private static String rightneighbourLookupName;

    private static ClientRemote leftClient;
    private static ClientRemote rightClient;

    private static boolean debugging;

    private static long lastRestorAttempt;


    public RestoreClient(int allSeats, int allPhilosopher, int allHungryPhilosopher, int eatTime, int meditationTime, int sleepTime, int runTimeInSeconds, String leftneighbourIP, String leftneighbourLookupName, String rightneighbourIP, String rightneighbourLookupName, ClientRemote leftClient, ClientRemote rightClient, boolean debugging){
        RestoreClient.allSeats = allSeats;
        RestoreClient.allPhilosopher = allPhilosopher;
        RestoreClient.allHungryPhilosopher = allHungryPhilosopher;
        RestoreClient.eatTime = eatTime;
        RestoreClient.meditationTime = meditationTime;
        RestoreClient.sleepTime = sleepTime;
        RestoreClient.endTime = 1000 * runTimeInSeconds+System.currentTimeMillis();

        RestoreClient.leftneighbourIP = leftneighbourIP;
        RestoreClient.leftneighbourLookupName = leftneighbourLookupName;
        RestoreClient.rightneighbourIP = rightneighbourIP;
        RestoreClient.rightneighbourLookupName = rightneighbourLookupName;

        RestoreClient.leftClient = leftClient;
        RestoreClient.rightClient = rightClient;
        RestoreClient.debugging = debugging;

    }

    public static int getAllSeats() {
        return allSeats;
    }

    public static int getAllPhilosopher() {
        return allPhilosopher;
    }

    public static int getAllHungryPhilosopher() {
        return allHungryPhilosopher;
    }

    public static int getEatTime() {
        return eatTime;
    }

    public static int getMeditationTime() {
        return meditationTime;
    }

    public static int getSleepTime() {
        return sleepTime;
    }

    public static long getEndTime() {
        return endTime;
    }

    public static String getLeftneighbourIP() {
        return leftneighbourIP;
    }

    public static String getLeftneighbourLookupName() {
        return leftneighbourLookupName;
    }

    public static String getRightneighbourIP() {
        return rightneighbourIP;
    }

    public static String getRightneighbourLookupName() {
        return rightneighbourLookupName;
    }

    public static boolean isDebugging() {
        return debugging;
    }

    public static ClientRemote getLeftClient() {
        return leftClient;
    }

    public static ClientRemote getRightClient() {
        return rightClient;
    }

    public static synchronized void startRestoring() {
        if(System.currentTimeMillis() > lastRestorAttempt + 1000){
            System.out.println("Due to client crash restoring was started.");
            restoreInformAll();
            ClientServiceImpl.getNeighbourList().remove(leftneighbourLookupName);
            restoreShareSeats();
            restorePhilosophers();
            restoreSetNewNeigbours();
            restoreFinishedInformAll();
            System.out.println("Restoring finished.");
            System.out.println(getRightneighbourLookupName());
            System.out.println(getLeftneighbourLookupName());
            System.out.println(ClientServiceImpl.getNeighbourList().size());
        }
        lastRestorAttempt = System.currentTimeMillis();
    }

    private static void restorePhilosophers() {
        boolean[] activePhilosophers = new boolean[RestoreClient.getAllPhilosopher()+RestoreClient.getAllHungryPhilosopher()];
        if(!rightneighbourLookupName.equals(leftneighbourLookupName)){
            try {
                activePhilosophers = rightClient.restoreGetPhilosophersCount(leftneighbourLookupName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        for(Philosopher philosopher : ClientServiceImpl.getPhilosophers()) {
            if(philosopher.isActive()){
                activePhilosophers[philosopher.getIdent()-1] =  true;
            }
        }
        for(int i = 0; i < activePhilosophers.length; i++) {
            if(!activePhilosophers[i]){
                restoreAwakePhilosopher(i);
            }
        }
    }

    private static void restoreAwakePhilosopher(int index) {
        Philosopher philosopher = ClientServiceImpl.getPhilosophers().get(index);
        philosopher.setActive(true);
        philosopher.setNewSeat(null);
        philosopher.setSeat(null);
        philosopher.setStatus(Status.MEDITATING);
        synchronized (philosopher.getMonitor()){
            philosopher.getMonitor().notifyAll();
        }

    }

    private static void restoreShareSeats() {
        try {
            Map<String, Integer> allSeats = new HashMap<String, Integer>();
            if(!rightneighbourLookupName.equals(leftneighbourLookupName)){
                allSeats.putAll(rightClient.getSeatsForRestoring(leftneighbourLookupName));
            }
            allSeats.put(Main.lookupName, TablePart.getTablePart().getSeats().size());
            int allSeatsCount = RestoreClient.getAllSeats();
            int allRemainingSeatsCount = 0;
            for(Map.Entry<String, Integer> entry : allSeats.entrySet()){
                allRemainingSeatsCount += entry.getValue();
            }
            int restoreSeatsCount = allSeatsCount - allRemainingSeatsCount;

            for(int i = 0; i < restoreSeatsCount; i++) {
                String fewestSeats = getFewestSeats(allSeats);
                ClientServiceImpl.restoreAddSeatCall(fewestSeats);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private static String getFewestSeats(Map<String, Integer> allSeats) {
        String currentFewest = "";
        int currentFewestCount = Integer.MAX_VALUE;
        for(Map.Entry<String, Integer> entry : allSeats.entrySet()) {
            if(entry.getValue() <= currentFewestCount) {
                currentFewestCount = entry.getValue();
                currentFewest = entry.getKey();
            }
        }
        return currentFewest;
    }

    private static void restoreFinishedInformAll() {
        ClientServiceImpl.setRestoringActive(false);
        if(rightneighbourLookupName.equals(leftneighbourLookupName)){
            try {
                rightClient.restoreFinishedInformAll(Main.lookupName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private static void restoreInformAll() {
        ClientServiceImpl.setRestoringActive(true);
        if(!rightneighbourLookupName.equals(leftneighbourLookupName)){
            try {
                rightClient.restoreInformAll(leftneighbourLookupName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private static void restoreSetNewNeigbours() {
        if(rightneighbourLookupName.equals(leftneighbourLookupName)) {
            rightneighbourLookupName = Main.lookupName;
            rightneighbourIP = Main.ownIP;
            try {
                rightClient = (ClientRemote)Naming.lookup("rmi://"+rightneighbourIP+"/"+rightneighbourLookupName);
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            leftneighbourLookupName = Main.lookupName;
            leftneighbourIP = Main.ownIP;
            try {
                leftClient = (ClientRemote)Naming.lookup("rmi://"+leftneighbourIP+"/"+leftneighbourLookupName);
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                rightClient.restoreSetRightNeigbour(leftneighbourLookupName, Main.lookupName, Main.ownIP);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            try {
                String[] newData = rightClient.restoreGetLookupNameAndIp(leftneighbourLookupName);
                leftneighbourLookupName = newData[0];
                leftneighbourIP = newData[1];
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            try {
                leftClient = (ClientRemote)Naming.lookup("rmi://"+leftneighbourIP+"/"+leftneighbourLookupName);
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
    }

    public static void setLeftneighbourIP(String leftneighbourIP) {
        RestoreClient.leftneighbourIP = leftneighbourIP;
    }

    public static void setLeftneighbourLookupName(String leftneighbourLookupName) {
        RestoreClient.leftneighbourLookupName = leftneighbourLookupName;
    }

    public static void setRightneighbourIP(String rightneighbourIP) {
        RestoreClient.rightneighbourIP = rightneighbourIP;
    }

    public static void setRightneighbourLookupName(String rightneighbourLookupName) {
        RestoreClient.rightneighbourLookupName = rightneighbourLookupName;
    }

    public static void setLeftClient(ClientRemote leftClient) {
        RestoreClient.leftClient = leftClient;
    }

    public static void setRightClient(ClientRemote rightClient) {
        RestoreClient.rightClient = rightClient;
    }
}
