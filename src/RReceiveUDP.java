import edu.utulsa.unet.RReceiveUDPI;

public class RReceiveUDP implements RReceiveUDPI
{
    @Override
    public boolean setMode(int var1)
    {
        return false;
    }

    @Override
    public int getMode()
    {
        return 0;
    }

    @Override
    public boolean setModeParameter(long var1)
    {
        return false;
    }

    @Override
    public long getModeParameter()
    {
        return 0;
    }

    @Override
    public void setFilename(String var1)
    {

    }

    @Override
    public String getFilename()
    {
        return null;
    }

    @Override
    public boolean setLocalPort(int var1)
    {
        return false;
    }

    @Override
    public int getLocalPort()
    {
        return 0;
    }

    @Override
    public boolean receiveFile()
    {
        return false;
    }
}
