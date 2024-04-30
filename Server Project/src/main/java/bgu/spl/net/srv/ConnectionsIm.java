package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsIm<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> idToConnection = new ConcurrentHashMap<Integer, BlockingConnectionHandler<T>>();

    @Override
    public void connect(int connectionId, BlockingConnectionHandler<T> handler) {
        // TODO Auto-generated method stub

        this.idToConnection.putIfAbsent(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        // TODO Auto-generated method stub
        // According to Hedi - No Synchronization needed in Server implementation.
        if (msg == null) {
            return false;
        }
        if (idToConnection.get(connectionId) != null) {
            idToConnection.get(connectionId).send(msg);
            return true;
        }
        return false;

    }

    @Override
    public void disconnect(int connectionId) {
        // TODO Auto-generated method stub
        if (idToConnection.contains(connectionId)) {
            try {
                this.idToConnection.get(connectionId).close();
            } catch (Exception e) {
                // TODO: handle
            }

            idToConnection.remove(connectionId);
        }
    }

}
