package org.helioviewer.jhv.plugins.pfss;

import org.helioviewer.jhv.base.Colors;
import org.helioviewer.jhv.opengl.BufVertex;
import org.helioviewer.jhv.plugins.pfss.data.PfssData;

class PfssLine {

    private static final byte[] openFieldColor = Colors.Red;
    private static final byte[] loopColor = Colors.White;
    private static final byte[] insideFieldColor = Colors.Blue;

    private static void computeBrightColor(float b, byte[] brightColor) {
        if (b > 0) {
            byte bb = (byte) (255 * (1f - b));
            brightColor[0] = (byte) 255;
            brightColor[1] = bb;
            brightColor[2] = bb;
        } else {
            byte bb = (byte) (255 * (1f + b));
            brightColor[0] = bb;
            brightColor[1] = bb;
            brightColor[2] = (byte) 255;
        }
        brightColor[3] = (byte) 255;
    }

    static void calculatePositions(PfssData data, int detail, boolean fixedColor, double radius, BufVertex lineBuf) {
        float[][] linex = data.linex;
        float[][] liney = data.liney;
        float[][] linez = data.linez;
        float[][] lines = data.lines;
        int nlines = linex.length;
        int points = linex[0].length;

        byte[] brightColor = new byte[4];
        byte[] oneColor = loopColor;

        for (int j = 0; j < nlines; j++) {
            if (j % (PfssSettings.MAX_DETAIL + 1) <= detail) {
                for (int i = 0; i < points; i++) {
                    float x = linex[j][i];
                    float y = liney[j][i];
                    float z = linez[j][i];
                    double r = Math.sqrt(x * x + y * y + z * z);

                    float b = lines[j][i]; // this can be index in LUT
                    computeBrightColor(b, brightColor);

                    if (i == 0) {
                        lineBuf.putVertex(x, z, -y, 1, Colors.Null);

                        if (fixedColor) {
                            float xo = linex[j][points - 1];
                            float yo = liney[j][points - 1];
                            float zo = linez[j][points - 1];
                            double ro = Math.sqrt(xo * xo + yo * yo + zo * zo);

                            if (Math.abs(r - ro) < 2.5 - 1.0 - 0.2) {
                                oneColor = loopColor;
                            } else if (b < 0) {
                                oneColor = insideFieldColor;
                            } else {
                                oneColor = openFieldColor;
                            }
                        }
                    }

                    lineBuf.putVertex(x, z, -y, 1, r > radius ? Colors.Null : (fixedColor ? oneColor : brightColor));
                    if (i == points - 1) {
                        lineBuf.repeatVertex(Colors.Null);
                    }
                }
            }
        }
    }

}
