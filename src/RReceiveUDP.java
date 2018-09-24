import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;

public class RReceiveUDP implements RReceiveUDPI
{
    /***
     * The purpose of this class is to be able to return multiple byte arrays with the unpackPacket method.
     */
    public class Packet
    {
        public Packet(int dataSize)
        {
            seqNum = new byte[SEQSIZE];
            data = new byte[dataSize];
        }
        public byte [] seqNum;
        public byte [] data;
        public byte flag;
    }

    /***
     * This method functions correctly. This will unpack a packet into sequence number and data.
     * @param buffer
     * @return
     */
    private Packet unpackPacket(byte [] buffer)
    {
        Packet packet = new Packet(buffer.length - SEQSIZE - 1);
        for (int i = 0; i < buffer.length; i++)
        {
            if (i == 0)
            {
                packet.flag = buffer[i];
            }
            else if (i < SEQSIZE + 1)
            {
                packet.seqNum[i - 1] = buffer[i];
            }
            else
            {
                packet.data[i - SEQSIZE - 1] = buffer[i];
            }
        }
        return packet;
    }

    private byte [] createACK(byte [] data, int number)
    {
        int flag = 1;
        byte [] tseq = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(number).array();
        byte [] seq = new byte[SEQSIZE];
        for (int i = tseq.length - (tseq.length - SEQSIZE), j = 0; i < tseq.length; i++, j++)
        {
            seq[j] = tseq[i];
        }
        byte [] packet = new byte[seq.length + data.length + 1];
        switch (flag)
        {
            // sending packet to receiver
            case 0:
                packet[0] = 0;
                break;
            // sending ack to sender
            case 1:
                packet[0] = 1;
        }
        System.arraycopy(seq, 0, packet, 1, seq.length);
        System.arraycopy(data, 0, packet, seq.length + 1, data.length);
        return packet;
    }

    /***
     * This method decodes a 2 byte array as an unsigned int then signs it so we don't have problems with having a leading 1.
     * @param seq
     * @return signed sequence number.
     */
    private int decodeSeq(byte [] seq)
    {
        return (((seq[0] & 0xff) << 8) | (seq[1] & 0xff));
    }

    @Override
    public boolean setMode(int var1)
    {
        if (var1 == 0 || var1 == 1)
        {
            mode = var1;
            return true;
        }
        return false;
    }

    @Override
    public int getMode()
    {
        return mode;
    }

    @Override
    public boolean setModeParameter(long var1)
    {
        if (mode == 1 && var1 > 2)
        {
            windowSize = var1;
            return true;
        }
        return false;
    }

    @Override
    public long getModeParameter()
    {
        return windowSize;
    }

    @Override
    public void setFilename(String var1)
    {
        filename = var1;
    }

    @Override
    public String getFilename()
    {
        return filename;
    }

    @Override
    public boolean setLocalPort(int var1)
    {
        if (var1 > 1024 && var1 < 65536)
        {
            port = var1;
            return true;
        }
        return false;
    }

    @Override
    public int getLocalPort()
    {
        return port;
    }

    @Override
    public boolean receiveFile()
    {
        receivedPackets = new ArrayList<>();
        try
        {
            UDPSocket socket = new UDPSocket(port);
            while (true)
            {
                byte[] buffer = new byte[socket.getSendBufferSize()];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                System.out.println("[RECEIVER]Waiting for packet on port " + port);
                socket.receive(datagramPacket);
                Packet packet = unpackPacket(datagramPacket.getData());
                // Check if this packet is already in the list
                boolean isIn = receivedPackets.contains(packet);
                if (!isIn)
                {
                    receivedPackets.add(decodeSeq(packet.seqNum) + 1, packet);
                }
                int seq = decodeSeq(packet.seqNum);
                System.out.println("[RECEIVER]Received packet " + seq);
                if (seq == lastReceivedPacket + 1)
                {
                    lastReceivedPacket++;
                    byte [] ACK = createACK(("").getBytes(), seq);
                    socket.send(new DatagramPacket(ACK, ACK.length, datagramPacket.getAddress(), datagramPacket.getPort()));
                }
            }
        }
        catch (SocketException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return false;
    }


    // Globals:
    private int lastReceivedPacket = -1;
    private int mode = 0;
    private int port = 12987;
    private long windowSize = 256;
    private int outstandingFrames = 1;
    private String ip = "localhost";
    private String filename = null;
    private ArrayList<Packet> receivedPackets;
    private final int SEQSIZE = 2;
}
