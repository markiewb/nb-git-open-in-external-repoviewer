/* 
 * Copyright 2015 markiewb.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.markiewb.netbeans.plugin.git.openinexternalviewer.actions;

import de.markiewb.netbeans.plugin.git.openinexternalviewer.git.GitUtils;
import de.markiewb.netbeans.plugin.git.openinexternalviewer.placeholders.EditorPlaceHolderResolver;
import de.markiewb.netbeans.plugin.git.openinexternalviewer.placeholders.PlaceHolderResolvers;
import de.markiewb.netbeans.plugin.git.openinexternalviewer.placeholders.WCPlaceHolderResolver;
import de.markiewb.netbeans.plugin.git.openinexternalviewer.strategies.RepoStrategy;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.libs.git.GitBranch;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;

/**
 * Popup menu for a {@link RepoStrategy}.
 *
 * @author markiewb
 */
public abstract class AbstractRepositoryPopupAction extends AbstractAction implements ActionListener, Presenter.Popup {

    private static final Logger LOG = Logger.getLogger(AbstractRepositoryPopupAction.class.getName());

    @Override
    public void actionPerformed(ActionEvent e) {
        //NOP
    }

    @Override
    public JMenuItem getPopupPresenter() {
        RepoStrategyConfig config = detectRepositoryStrategy();
        if (null == config) {
            return null;
        }
        JMenu main = new JMenu(config.strategy.getLabel());
        List<JMenuItem> items = createMenuItems(config);
        setEnabled(!items.isEmpty());
        for (JMenuItem item : items) {
            main.add(item);
        }
        return main;
    }

    protected List<JMenuItem> createMenuItems(RepoStrategyConfig strategyC) {
        if (null == strategyC) {
            return Collections.emptyList();
        }
        List<JMenuItem> items = new ArrayList<JMenuItem>();
        for (final RepoStrategy.Type type : getSUPPORTEDTYPES()) {
            final String url = strategyC.strategy.getUrl(type, strategyC.remoteURI, strategyC.resolvers);
            if (null == url) {
                continue;
            }
            JMenuItem jMenuItem = new JMenuItem(new OpenURLAction(url, type.getLabel(), this));
            items.add(jMenuItem);
        }
        return items;
    }

    protected RepoStrategyConfig detectRepositoryStrategy() {
        Lookup lkp = Utilities.actionsGlobalContext();
        RepoStrategyConfig result = null;
        setEnabled(false);
        //only support one project selected project
        Collection<? extends FileObject> lookupAll = lkp.lookupAll(FileObject.class);
        if (lookupAll.size() >= 2) {
            return result;
        }
        FileObject gitRepoDirectory = GitUtils.getGitRepoDirectory(lookupAll.iterator().next());
        if (gitRepoDirectory == null) {
            return result;
        }
        GitBranch activeBranch = GitUtils.getActiveBranch(gitRepoDirectory);
        if (activeBranch == null) {
            return result;
        }
        if (activeBranch.getTrackedBranch() == null) {
            //TODO support detached heads
            //TODO support tags
            return result;
        } else {
            final String remoteBranchName = activeBranch.getTrackedBranch().getName();
            //split "origin/master" to "origin" "master"
            //split "orgin/feature/myfeature" to "origin" "feature/myfeature"
            int indexOf = remoteBranchName.indexOf("/");
            if (indexOf <= 0 || remoteBranchName.startsWith("/") || remoteBranchName.endsWith("/")) {
                // no slash found OR
                // slash is the first char? NOGO
                //slash at the end? NOGO
                return result;
            }
            {
                final String origin = remoteBranchName.substring(0, indexOf);
                final String remoteName = remoteBranchName.substring(indexOf + 1);
                JTextComponent ed = EditorRegistry.lastFocusedComponent();
                EditorPlaceHolderResolver editorPlaceHolderResolver = new EditorPlaceHolderResolver(ed, gitRepoDirectory);
                WCPlaceHolderResolver wcPlaceHolderResolver = new WCPlaceHolderResolver(remoteName, activeBranch.getId());
                PlaceHolderResolvers resolvers = new PlaceHolderResolvers(wcPlaceHolderResolver, editorPlaceHolderResolver);
                final String remoteURI = GitUtils.getRemote(gitRepoDirectory, origin);
                return getStrategy(remoteURI, resolvers);
            }
        }
    }

    protected abstract EnumSet<RepoStrategy.Type> getSUPPORTEDTYPES();

    protected RepoStrategyConfig getStrategy(String remoteURI, PlaceHolderResolvers resolvers) {
        Collection<? extends RepoStrategy> strategies = Lookup.getDefault().lookupAll(RepoStrategy.class);
        for (RepoStrategy strategy : strategies) {
            boolean supported;
            try {
                for (final RepoStrategy.Type type : getSUPPORTEDTYPES()) {
                    supported = null != strategy.getUrl(type, remoteURI, resolvers);
                    if (supported) {
                        RepoStrategyConfig config = new RepoStrategyConfig();
                        config.remoteURI = remoteURI;
                        config.strategy = strategy;
                        config.resolvers = resolvers;
                        return config;
                    }
                }
            } catch (Exception e) {
                LOG.warning(String.format("caught exception while calling strategy %s with %s :\n%s", strategy, remoteURI, e));
                supported = false;
            }
        }
        return null;
    }

    protected class RepoStrategyConfig {

        RepoStrategy strategy;
        String remoteURI;
        PlaceHolderResolvers resolvers;
    }

}