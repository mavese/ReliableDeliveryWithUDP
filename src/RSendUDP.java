import java.net.DatagramSocket;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import edu.utulsa.unet.RSendUDPI;


public class RSendUDP implements RSendUDPI
{

    public void init(String fname)
    {
        File file = new File(fname);
        try
        {
            DatagramSocket socket = new DatagramSocket(2024);
            byte [] fileBuffer = Files.readAllBytes(file.toPath());
            int fileSize = fileBuffer.length;
            int packetSize = socket.getSendBufferSize();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /***
     * This method currently functions correctly it has been tested. When pulling sequence number out
     * must make sure to put it in an unsigned integer as the leading bit could be one
     * @param data the data to pack in packet
     * @param number the sequence number of this packet
     * @return byte array that is the packet to send.
     */
    private byte [] packPacket(byte [] data, Integer number)
    {
        byte [] tseq = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(number).array();
        for (byte b:tseq)
        {
            System.out.println(b);
        }
        System.out.println("\n");
        byte [] seq = new byte[SEQSIZE];
        int j = 0;
        for (int i = tseq.length - (tseq.length - SEQSIZE); i < tseq.length; i++, j++)
        {
            seq[j] = tseq[i];
        }
        byte [] packet = new byte[seq.length + data.length];
        System.arraycopy(seq, 0, packet, 0, seq.length);
        System.arraycopy(data, 0, packet, seq.length, data.length);
        return packet;
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
    public boolean setTimeout(long var1)
    {
        if (var1 > 0)
        {
            timeout = var1;
            return true;
        }
        return false;
    }

    @Override
    public long getTimeout()
    {
        return timeout;
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
    public boolean setReceiver(InetSocketAddress var1)
    {
        receiver = var1;
        return true;
    }

    @Override
    public InetSocketAddress getReceiver()
    {
        return receiver;
    }


    @Override
    public boolean sendFile()
    {
        return false;
    }


    // Globals:
    public int mode = 0;
    public int packetSize;
    public int port = 12987;
    public long timeout = 1000;
    public long windowSize = 256;
    public int outstandingFrames = 1;
    public String ip = "localhost";
    public String filename;
    public InetSocketAddress receiver;
    private final int SEQSIZE = 2;

}
