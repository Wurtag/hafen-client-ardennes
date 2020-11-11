package haven.purus.pbot;

import haven.Tex;
import haven.TexI;
import haven.Text;
import haven.UI;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class PBotScriptlistItemOld {

    private String name;
    private File scriptFile;
    private Tex iconTex, nameTex;
    public final UI ui;

    public PBotScriptlistItemOld(UI ui, String name, File scriptFile) {
        this.name = name;
        this.scriptFile = scriptFile;
        this.ui = ui;

        File icon = new File("scripts/" + scriptFile.getName().substring(0, scriptFile.getName().lastIndexOf(".")) + ".png");
        if (icon.exists()) {
            try {
                this.iconTex = new TexI(ImageIO.read(icon));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.nameTex = Text.render(name.substring(0, name.length() - 8)).tex();
    }

    public void runScript() {
        PBotScriptmanagerOld.startScript(ui, scriptFile);
    }

    public Tex getIconTex() {
        return this.iconTex;
    }

    public Tex getNameTex() {
        return this.nameTex;
    }

    public String getName() {
        return this.name;
    }

}
