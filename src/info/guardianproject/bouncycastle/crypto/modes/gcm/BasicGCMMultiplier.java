package info.guardianproject.bouncycastle.crypto.modes.gcm;

import info.guardianproject.bouncycastle.util.Arrays;

public class BasicGCMMultiplier implements GCMMultiplier
{
    private byte[] H;

    public void init(byte[] H)
    {
        this.H = Arrays.clone(H);
    }

    public void multiplyH(byte[] x)
    {
        GCMUtil.multiply(x, H);
    }
}
