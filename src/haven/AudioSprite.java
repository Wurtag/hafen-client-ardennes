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

package haven;

import haven.Audio.CS;

import java.util.ArrayList;
import java.util.List;

public class AudioSprite {
    public static List<Resource.Audio> clips(Resource res, String id) {
        List<Resource.Audio> cl = new ArrayList<Resource.Audio>();
        for (Resource.Audio clip : res.layers(Resource.audio)) {
            if (clip.id == id)
                cl.add(clip);
        }
        return (cl);
    }

    public static Resource.Audio randoom(Resource res, String id) {
        List<Resource.Audio> cl = clips(res, id);
        if (!cl.isEmpty()) {
            int rnd = (int) (Math.random() * cl.size());
            if (rnd == 1 && "sfx/items/pickaxe".equals(res.name))
                rnd = 0;
            return cl.get(rnd);
        }
        return (null);
    }

    public static final Sprite.Factory fact = new Sprite.Factory() {
        public Sprite create(Sprite.Owner owner, Resource res, Message sdt) {
            final UI ui;
            if (owner instanceof Gob) {
                ui = ((Gob) owner).glob.ui.get();
            } else {
                ui = null;
            }

            if (DefSettings.PAUSED.get() || DefSettings.NOGOBAUDIO.get() ||
                    (ui != null && !MainFrame.instance.p.isActiveUI(ui))) {
                return new IgnoreSprite(owner, res);
            }
            {
                Resource.Audio clip = randoom(res, "cl");
                if (clip != null)
                    return (new ClipSprite(owner, res, clip));
            }
            {
                List<Resource.Audio> clips = clips(res, "rep");
                if (!clips.isEmpty())
                    return (new RepeatSprite(owner, res, randoom(res, "beg"), clips, randoom(res, "end")));
            }
            {
                if ((res.layer(Resource.audio, "amb") != null) || (res.layer(ClipAmbiance.Desc.class) != null))
                    return (new Ambience(owner, res));
            }
            return (null);
        }
    };

    public static class IgnoreSprite extends Sprite {
        public IgnoreSprite(Owner owner, Resource res) {
            super(owner, res);
        }

        public boolean setup(RenderList r) {
            return false;
        }

        public boolean tick(int dt) {
            return true;
        }

        public Object staticp() {
            return (CONSTANS);
        }
    }


    public static class ClipSprite extends Sprite {
        public final ActAudio.PosClip clip;
        private boolean done = false;

        public ClipSprite(Owner owner, Resource res, Resource.Audio clip) {
            super(owner, res);
            haven.Audio.CS stream = clip.stream();
            if (Config.sfxchipvol != 1.0 && "sfx/chip".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxchipvol);
            else if ("sfx/squeak".equals(res.name))
                stream = new Audio.VolAdjust(stream, 0.2);
            else if (Config.sfxquernvol != 1.0 && "sfx/terobjs/quern".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxquernvol);
            else if (Config.sfxdoorvol != 1.0 && "sfx/terobjs/arch/door".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxdoorvol);
            else if (Config.sfxclapvol != 1.0 && "sfx/borka/clap".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxclapvol);
            else if (Config.sfxchatvol != 1.0 && "sfx/hud/chat".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxchatvol);
            else if (Config.sfxwhistlevol != 1.0 && "sfx/borka/whistle".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxwhistlevol);
            else if (Config.sfxdingvol != 1.0 && "sfx/msg".equals(res.name))
                stream = new Audio.VolAdjust(stream, Config.sfxdingvol);


            this.clip = new ActAudio.PosClip(new Audio.Monitor(stream) {
                protected void eof() {
                    super.eof();
                    done = true;
                }
            });
        }

        public boolean setup(RenderList r) {
            r.add(clip, null);
            return (false);
        }

        public boolean tick(int dt) {
            /* XXX: This is slightly bad, because virtual sprites that
             * are stuck as loading (by getting outside the map, for
             * instance), never play and therefore never get done,
             * effectively leaking. For now, this is seldom a problem
             * because in practive most (all?) virtual audio-sprites
             * come from Skeleton.FxTrack which memoizes its origin
             * instead of asking the map for it, but also see comment
             * in glsl.MiscLib.maploc. Solve pl0x. */
            return (done);
        }

        public Object staticp() {
            return (CONSTANS);
        }
    }

    public static class RepeatSprite extends Sprite implements Gob.Overlay.CDel {
        private ActAudio.PosClip clip;
        private final Resource.Audio end;

        public RepeatSprite(Owner owner, Resource res, final Resource.Audio beg, final List<Resource.Audio> clips, Resource.Audio end) {
            super(owner, res);
            this.end = end;
            CS rep = new Audio.Repeater() {
                private boolean f = true;

                public CS cons() {
                    if (f && (beg != null)) {
                        f = false;
                        return (beg.stream());
                    }
                    return (clips.get((int) (Math.random() * clips.size())).stream());
                }
            };
            if (Config.sfxbeehivevol != 1.0 && "sfx/kritter/beeswarm".equals(res.name))
                rep = new Audio.VolAdjust(rep, Config.sfxbeehivevol);

            this.clip = new ActAudio.PosClip(rep);
        }


        public boolean setup(RenderList r) {
            if (clip != null)
                r.add(clip, null);
            return (false);
        }

        public boolean tick(int dt) {
            return (clip == null);
        }

        public void delete() {
            if (end != null)
                clip = new ActAudio.PosClip(new Audio.Monitor(end.stream()) {
                    protected void eof() {
                        super.eof();
                        RepeatSprite.this.clip = null;
                    }
                });
            else
                clip = null;
        }

        public Object staticp() {
            return (CONSTANS);
        }
    }

    public static class Ambience extends Sprite {
        public final Rendered amb;

        public Ambience(Owner owner, Resource res) {
            super(owner, res);
            ClipAmbiance.Desc clamb = res.layer(ClipAmbiance.Desc.class);
            if (clamb != null) {
                this.amb = clamb.spr;
            } else {
                if (Config.sfxfirevol != 1.0 && "sfx/fire".equals(res.name))
                    this.amb = new ActAudio.Ambience(res, Config.sfxfirevol);
                else if (Config.sfxcauldronvol != 1.0 && res.basename().contains("cauldron"))
                    this.amb = new ActAudio.Ambience(res, Config.sfxcauldronvol);
                else
                    this.amb = new ActAudio.Ambience(res);
            }
        }

        public boolean setup(RenderList r) {
            r.add(amb, null);
            return (false);
        }

        public Object staticp() {
            return (CONSTANS);
        }
    }
}
