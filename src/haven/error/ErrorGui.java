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

package haven.error;

import haven.Config;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public abstract class ErrorGui extends JDialog implements ErrorStatus {
    private JPanel details;
    private JButton closebtn, cbbtn, hidebtn;
    private JTextArea exbox;
    private JScrollPane infoc, exboxc;
    private Thread reporter;
    private boolean done;

    public ErrorGui(java.awt.Frame parent) {
        super(parent, "Haven error!", true);
        setMinimumSize(new Dimension(300, 100));
        setResizable(false);
        add(new JPanel() {{
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            add(new JLabel(" An error has occurred! Please notify the client developer."));

            add(new JPanel() {{
                setLayout(new FlowLayout());
                setAlignmentX(0);
                add(cbbtn = new JButton("Copy to Clipboard") {{
                    addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent ev) {
                            StringSelection exc = new StringSelection(exbox.getText());
                            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                            cb.setContents(exc, null);
                            ErrorGui.this.pack();
                        }
                    });
                }});
                add(closebtn = new JButton("Close") {{
                    addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent ev) {
                            ErrorGui.this.dispose();
                            synchronized (ErrorGui.this) {
                                done = true;
                                ErrorGui.this.notifyAll();
                            }
                            System.exit(1);
                        }
                    });
                }});
                add(hidebtn = new JButton("Hide") {{
                    addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent ev) {
                            ErrorGui.this.dispose();
                            synchronized (ErrorGui.this) {
                                done = true;
                                ErrorGui.this.notifyAll();
                            }
                            //System.exit(1);
                        }
                    });
                }});
            }});
            add(details = new JPanel() {{
                setLayout(new BorderLayout());
                setAlignmentX(0);
                setVisible(true);
                add(exboxc = new JScrollPane(exbox = new JTextArea(15, 80) {{
                    setEditable(false);
                }}) {{
                    setVisible(true);
                }});
            }});
        }});
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent ev) {
                ErrorGui.this.dispose();
                synchronized (ErrorGui.this) {
                    done = true;
                    ErrorGui.this.notifyAll();
                }
                reporter.interrupt();
                System.exit(1);
            }
        });
        pack();
        setLocationRelativeTo(parent);
    }

    public boolean goterror(Report r) {
        reporter = Thread.currentThread();
        java.io.StringWriter w = new java.io.StringWriter();
        r.t.printStackTrace(new java.io.PrintWriter(w));
        final String tr = w.toString();
        SwingUtilities.invokeLater(() -> {
            String details = String.format("%s.%s\n%s, %s\n%s\n\n%s",
                    Config.version,
                    Config.gitrev.substring(0, 8),
                    r.props.get("os"), r.props.get("java"),
                    r.props.get("gpu"),
                    tr);
            exbox.setText(details);
            pack();
            exbox.setCaretPosition(0);
            setVisible(true);
        });
        return (true);
    }

    public void done(final String ctype, final String info) {
        done = false;

        synchronized (this) {
            try {
                while (!done)
                    wait();
            } catch (InterruptedException e) {
                throw (new Error(e));
            }
        }
        errorsent();
    }

    public abstract void errorsent();
}
