/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven.resutil;

import haven.BGL;
import haven.GLState;
import haven.GOut;
import haven.Material;
import haven.PView;
import haven.Resource;
import haven.TexCube;
import haven.glsl.MiscLib;
import haven.glsl.ShaderMacro;
import haven.glsl.Uniform;

import javax.media.opengl.GL;
import java.awt.Color;

import static haven.glsl.Cons.add;
import static haven.glsl.Cons.l;
import static haven.glsl.Cons.mul;
import static haven.glsl.Cons.neg;
import static haven.glsl.Cons.reflect;
import static haven.glsl.Cons.textureCube;
import static haven.glsl.Cons.vec4;
import static haven.glsl.Type.MAT3;
import static haven.glsl.Type.SAMPLERCUBE;
import static haven.glsl.Type.VEC3;

@Material.ResName("envref")
public class EnvMap extends GLState {
    public static final Slot<EnvMap> slot = new Slot<EnvMap>(Slot.Type.DRAW, EnvMap.class);
    private static final Uniform csky = new Uniform(SAMPLERCUBE);
    private static final Uniform ccol = new Uniform(VEC3);
    private static final Uniform icam = new Uniform(MAT3);
    private static final TexCube sky = WaterTile.sky;
    public final float[] col;
    private TexUnit tsky;

    public EnvMap(Color col) {
        this.col = new float[]{
                col.getRed() / 255.0f,
                col.getGreen() / 255.0f,
                col.getBlue() / 255.0f,
        };
    }

    public EnvMap(Resource res, Object... args) {
        this((Color) args[0]);
    }

    private static final ShaderMacro shader = prog -> {
        prog.dump = true;
        prog.fctx.fragcol.mod(in -> {
            return (add(in, mul(textureCube(csky.ref(), neg(mul(icam.ref(),
                    reflect(MiscLib.fragedir(prog.fctx).depref(),
                            MiscLib.frageyen(prog.fctx).depref())))),
                    vec4(ccol.ref(), l(0.0)))));
        }, 90);
    };

    public ShaderMacro shader() {
        return (shader);
    }

    public boolean reqshader() {
        return (true);
    }

    public void reapply(GOut g) {
        g.gl.glUniform1i(g.st.prog.uniform(csky), tsky.id);
        g.gl.glUniform3fv(g.st.prog.uniform(ccol), 1, col, 0);
        g.gl.glUniformMatrix3fv(g.st.prog.uniform(icam), 1, false, PView.camxf(g).transpose().trim3(), 0);
    }

    public void apply(GOut g) {
        BGL gl = g.gl;
        (tsky = g.st.texalloc()).act(g);
        gl.glBindTexture(GL.GL_TEXTURE_CUBE_MAP, sky.glid(g));
        reapply(g);
    }

    public void unapply(GOut g) {
        tsky.act(g);
        g.gl.glBindTexture(GL.GL_TEXTURE_CUBE_MAP, null);
        tsky.free();
        tsky = null;
    }

    public void prep(Buffer buf) {
        buf.put(slot, this);
    }
}
