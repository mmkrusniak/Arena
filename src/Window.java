import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;


public class Window extends JFrame {

    public Window(MainPanel mainPanel) {
        LayoutManager layout = new GridBagLayout();
        setLayout(layout);
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        JPanel genomePanel = new JPanel();
        JPanel activeSetPanel = new JPanel();
        JPanel usedSetPanel = new JPanel();


        //Add the main panel:
        JPanel mainContainer = new JPanel();
        mainContainer.setLayout(new BorderLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.fill = c.BOTH;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        c.gridwidth = 1;
        c.ipadx = 5;
        c.ipady = 5;
        c.weightx = .7;
        c.weighty = .5;
        mainContainer.add(mainPanel, BorderLayout.CENTER);
        mainContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createLoweredBevelBorder()
            ));
        this.add(mainContainer, c);

        //Add the info panel:
        //Because of the way borders work we have to use multiple panels...
        JTabbedPane infoPanel = new JTabbedPane();
        JPanel infoPanelPanel = new JPanel();
        infoPanelPanel.setLayout(new BorderLayout());
        infoPanelPanel.add(infoPanel, BorderLayout.CENTER);
        infoPanel.setBackground(new Color(0, 155, 0));
        c = new GridBagConstraints();
        c.fill = c.BOTH;
        c.gridx = 1;
        c.gridy = 0;
        c.gridheight = 1;
        c.gridwidth = 1;
        c.ipadx = 5;
        c.ipady = 5;
        c.weightx = .4;
        c.weighty = .5;

        infoPanel.addTab("Genome", genomePanel);
        infoPanel.addTab("Active Set", activeSetPanel);
        infoPanel.addTab("Used Set", usedSetPanel);

        infoPanelPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(infoPanelPanel, c);

        //Add the control panel:
        JPanel controlPanel = new JPanel();
        JPanel controlPanelPanel = new JPanel();
        controlPanel.setBackground(new Color(100, 150, 250));

        c = new GridBagConstraints();
        c.fill = c.BOTH;
        c.gridx = 1;
        c.gridy = 1;
        c.gridheight = 1;
        c.gridwidth = 1;
        c.ipadx = 5;
        c.ipady = 5;
        c.weightx = .4;
        c.weighty = .3;
        controlPanelPanel.setLayout(new BorderLayout());
        controlPanelPanel.add(controlPanel, BorderLayout.CENTER);
        controlPanelPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(controlPanelPanel, c);
    }
}