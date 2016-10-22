/*
 * Copyright (c) 2016. See AUTHORS file.
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
package com.mbrlabs.mundus.editor.ui.modules;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Selection;
import com.badlogic.gdx.utils.Align;
import com.kotcrab.vis.ui.util.dialog.Dialogs;
import com.kotcrab.vis.ui.util.dialog.Dialogs.InputDialog;
import com.kotcrab.vis.ui.util.dialog.InputDialogAdapter;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTree;
import com.mbrlabs.mundus.commons.assets.TerrainAsset;
import com.mbrlabs.mundus.commons.scene3d.GameObject;
import com.mbrlabs.mundus.commons.scene3d.SceneGraph;
import com.mbrlabs.mundus.commons.scene3d.components.Component;
import com.mbrlabs.mundus.commons.terrain.Terrain;
import com.mbrlabs.mundus.editor.core.Inject;
import com.mbrlabs.mundus.editor.core.Mundus;
import com.mbrlabs.mundus.editor.core.project.ProjectContext;
import com.mbrlabs.mundus.editor.core.project.ProjectManager;
import com.mbrlabs.mundus.editor.events.AssetImportEvent;
import com.mbrlabs.mundus.editor.events.GameObjectSelectedEvent;
import com.mbrlabs.mundus.editor.events.ProjectChangedEvent;
import com.mbrlabs.mundus.editor.events.SceneChangedEvent;
import com.mbrlabs.mundus.editor.events.SceneGraphChangedEvent;
import com.mbrlabs.mundus.editor.history.Command;
import com.mbrlabs.mundus.editor.history.CommandHistory;
import com.mbrlabs.mundus.editor.history.commands.DeleteCommand;
import com.mbrlabs.mundus.editor.shader.Shaders;
import com.mbrlabs.mundus.editor.tools.ToolManager;
import com.mbrlabs.mundus.editor.ui.Ui;
import com.mbrlabs.mundus.editor.utils.Log;
import com.mbrlabs.mundus.editor.utils.TerrainUtils;

/**
 * Outline shows overview about all game objects in the scene
 *
 * @author Marcus Brummer, codenigma
 * @version 01-10-2016
 */
public class Outline extends VisTable
        implements ProjectChangedEvent.ProjectChangedListener, SceneChangedEvent.SceneChangedListener,
        SceneGraphChangedEvent.SceneGraphChangedListener, GameObjectSelectedEvent.GameObjectSelectedListener {

    private static final String TITLE = "Outline";
    private static final String TAG = Outline.class.getSimpleName();

    private VisTable content;
    private VisTree tree;
    private ScrollPane scrollPane;

    private DragAndDrop dragAndDrop;

    private RightClickMenu rightClickMenu;

    @Inject
    private Shaders shaders;
    @Inject
    private ToolManager toolManager;
    @Inject
    private ProjectManager projectManager;
    @Inject
    private CommandHistory history;

    public Outline() {
        super();
        Mundus.inject(this);
        Mundus.registerEventListener(this);
        setBackground("window-bg");

        rightClickMenu = new RightClickMenu();

        content = new VisTable();
        content.align(Align.left | Align.top);

        tree = new VisTree();
        tree.getSelection().setProgrammaticChangeEvents(false);
        scrollPane = new VisScrollPane(tree);
        scrollPane.setFlickScroll(false);
        scrollPane.setFadeScrollBars(false);
        content.add(scrollPane).fill().expand();

        add(new VisLabel(TITLE)).expandX().fillX().pad(3).row();
        addSeparator().row();
        add(content).fill().expand();

        setupDragAndDrop();
        setupListeners();
    }

    @Override
    public void onProjectChanged(ProjectChangedEvent projectChangedEvent) {
        // update to new sceneGraph
        Log.trace(TAG, "Project changed. Building scene graph.");
        buildTree(projectManager.current().currScene.sceneGraph);
    }

    @Override
    public void onSceneChanged(SceneChangedEvent sceneChangedEvent) {
        // update to new sceneGraph
        Log.trace(TAG, "Scene changed. Building scene graph.");
        buildTree(projectManager.current().currScene.sceneGraph);
    }

    @Override
    public void onSceneGraphChanged(SceneGraphChangedEvent sceneGraphChangedEvent) {
        Log.trace(TAG, "SceneGraph changed. Building scene graph.");
        buildTree(projectManager.current().currScene.sceneGraph);
    }

    private void setupDragAndDrop() {
        dragAndDrop = new DragAndDrop();

        // source
        dragAndDrop.addSource(new DragAndDrop.Source(tree) {
            @Override
            public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                Tree.Node node = tree.getNodeAt(y);
                if (node != null) {
                    payload.setObject(node);
                    return payload;
                }

                return null;
            }
        });

        // target
        dragAndDrop.addTarget(new DragAndDrop.Target(tree) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                // Select node under mouse if not over the selection.
                Tree.Node overNode = tree.getNodeAt(y);
                if (overNode == null && tree.getSelection().isEmpty()) {
                    return true;
                }
                if (overNode != null && !tree.getSelection().contains(overNode)) {
                    tree.getSelection().set(overNode);
                }
                return true;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                Tree.Node node = (Tree.Node) payload.getObject();
                ProjectContext context = projectManager.current();

                if (node != null) {
                    GameObject draggedGo = (GameObject) node.getObject();
                    Tree.Node newParent = tree.getNodeAt(y);

                    // check if a go is dragged in one of its' children or
                    // itself
                    if (newParent != null) {
                        GameObject parentGo = (GameObject) newParent.getObject();
                        if (parentGo.isChildOf(draggedGo)) {
                            return;
                        }
                    }
                    GameObject oldParent = draggedGo.getParent();

                    // remove child from old parent
                    draggedGo.remove();

                    // add to new parent
                    if (newParent == null) {
                        // recalculate position for root layer
                        Vector3 newPos;
                        Vector3 draggedPos = new Vector3();
                        draggedGo.getPosition(draggedPos);
                        // if moved from old parent
                        if (oldParent != null) {
                            // new position = oldParentPos + draggedPos
                            Vector3 parentPos = new Vector3();
                            oldParent.getPosition(parentPos);
                            newPos = parentPos.add(draggedPos);
                        } else {
                            // new local position = World position
                            newPos = draggedPos;
                        }
                        context.currScene.sceneGraph.addGameObject(draggedGo);
                        draggedGo.setLocalPosition(newPos.x, newPos.y, newPos.z);
                    } else {
                        GameObject parentGo = (GameObject) newParent.getObject();
                        // recalculate position
                        Vector3 parentPos = new Vector3();
                        Vector3 draggedPos = new Vector3();
                        // World coorinates
                        draggedGo.getPosition(draggedPos);
                        parentGo.getPosition(parentPos);

                        // if gameObject came from old parent
                        if (oldParent != null) {
                            // calculate oldParentPos + draggedPos
                            Vector3 oldParentPos = new Vector3();
                            oldParent.getPosition(oldParentPos);
                            draggedPos = oldParentPos.add(draggedPos);
                        }

                        // Local in releation to new parent
                        Vector3 newPos = draggedPos.sub(parentPos);
                        // add
                        parentGo.addChild(draggedGo);
                        draggedGo.setLocalPosition(newPos.x, newPos.y, newPos.z);
                    }

                    // update tree
                    buildTree(projectManager.current().currScene.sceneGraph);
                }
            }
        });
    }

    private void setupListeners() {

        scrollPane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                Ui.getInstance().setScrollFocus(scrollPane);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                Ui.getInstance().setScrollFocus(null);
            }

        });

        // right click menu listener
        tree.addListener(new InputListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (Input.Buttons.RIGHT != button) {
                    return;
                }

                Tree.Node node = tree.getNodeAt(y);
                GameObject go = null;
                if (node != null) {
                    go = (GameObject) node.getObject();
                }
                rightClickMenu.show(go, Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });

        // select listener
        tree.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Selection<Tree.Node> selection = tree.getSelection();
                if (selection != null && selection.size() > 0) {
                    GameObject go = (GameObject) selection.first().getObject();
                    projectManager.current().currScene.sceneGraph.setSelected(go);
                    toolManager.translateTool.gameObjectSelected(go);
                    Mundus.postEvent(new GameObjectSelectedEvent(go));
                }
            }
        });

    }

    /**
     * Building tree from game objects in sceneGraph, clearing previous
     * sceneGraph
     *
     * @param sceneGraph
     */
    private void buildTree(SceneGraph sceneGraph) {
        tree.clearChildren();

        for (GameObject go : sceneGraph.getGameObjects()) {
            addGoToTree(null, go);
        }
    }

    /**
     * Adding game object to outline
     *
     * @param treeParentNode
     * @param gameObject
     */
    private void addGoToTree(Tree.Node treeParentNode, GameObject gameObject) {
        Tree.Node leaf = new Tree.Node(new TreeNode(gameObject));
        leaf.setObject(gameObject);
        if (treeParentNode == null) {
            tree.add(leaf);
        } else {
            treeParentNode.add(leaf);
        }
        // Always expand after adding new node
        leaf.expandTo();
        if (gameObject.getChildren() != null) {
            for (GameObject goChild : gameObject.getChildren()) {
                addGoToTree(leaf, goChild);
            }
        }
    }

    /**
     * Removing game object from tree and outline
     *
     * @param go
     */
    private void removeGo(GameObject go) {
        // run delete command, updating sceneGraph and outline
        Command deleteCommand = new DeleteCommand(go, tree.findNode(go));
        history.add(deleteCommand);
        deleteCommand.execute(); // run delete
    }

    /**
     * Deep copy of all game objects
     * 
     * @param go
     *            the game object for cloning, with children
     * @param parent
     *            game object on which clone will be added
     */
    private void duplicateGO(GameObject go, GameObject parent) {
        Log.trace(TAG, "Duplicate [{}] with parent [{}]", go, parent);
        GameObject goCopy = new GameObject(go, projectManager.current().obtainID());

        // add copy to tree
        Tree.Node n = tree.findNode(parent);
        addGoToTree(n, goCopy);

        // add copy to scene graph
        parent.addChild(goCopy);

        // recursively clone child objects
        if (go.getChildren() != null) {
            for (GameObject child : go.getChildren()) {
                duplicateGO(child, goCopy);
            }
        }
    }

    @Override
    public void onGameObjectSelected(GameObjectSelectedEvent gameObjectSelectedEvent) {
        Tree.Node node = tree.findNode(gameObjectSelectedEvent.getGameObject());
        Log.trace(TAG, "Select game object [{}].", node.getObject());
        tree.getSelection().clear();
        tree.getSelection().add(node);
        node.expandTo();
    }

    /**
     * A node of the ui tree hierarchy.
     */
    private class TreeNode extends VisTable {

        private VisLabel name;

        public TreeNode(final GameObject go) {
            super();
            name = new VisLabel();
            add(name).expand().fill();
            name.setText(go.name);
        }
    }

    /**
     *
     */
    private class RightClickMenu extends PopupMenu {

        private MenuItem addEmpty;
        private MenuItem addTerrain;
        private MenuItem duplicate;
        private MenuItem rename;
        private MenuItem delete;

        private GameObject selectedGO;

        public RightClickMenu() {
            super();

            addEmpty = new MenuItem("Add Empty");
            addTerrain = new MenuItem("Add terrain");
            duplicate = new MenuItem("Duplicate");
            rename = new MenuItem("Rename");
            delete = new MenuItem("Delete");

            // add empty
            addEmpty.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    SceneGraph sceneGraph = projectManager.current().currScene.sceneGraph;
                    int id = projectManager.current().obtainID();
                    // the new game object
                    GameObject go = new GameObject(sceneGraph, GameObject.DEFAULT_NAME, id);
                    // update outline
                    if (selectedGO == null) {
                        // update sceneGraph
                        Log.trace(TAG, "Add empty game object [{}] in root node.", go);
                        sceneGraph.addGameObject(go);
                        // update outline
                        addGoToTree(null, go);
                    } else {
                        Log.trace(TAG, "Add empty game object [{}] child in node [{}].", go, selectedGO);
                        // update sceneGraph
                        selectedGO.addChild(go);
                        // update outline
                        Tree.Node n = tree.findNode(selectedGO);
                        addGoToTree(n, go);
                    }
                    Mundus.postEvent(new SceneGraphChangedEvent());
                }
            });

            // add terrainAsset
            addTerrain.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    try {
                        Log.trace(TAG, "Add terrain game object in root node.");
                        ProjectContext context = projectManager.current();
                        SceneGraph sceneGraph = context.currScene.sceneGraph;
                        final int goID = projectManager.current().obtainID();
                        final String name = "Terrain " + goID;
                        // create asset
                        TerrainAsset asset = context.assetManager.createTerraAsset(name,
                                Terrain.DEFAULT_VERTEX_RESOLUTION, Terrain.DEFAULT_SIZE);
                        asset.load();
                        asset.applyDependencies();

                        final GameObject terrainGO = TerrainUtils.createTerrainGO(sceneGraph, shaders.terrainShader,
                                goID, name, asset);
                        // update sceneGraph
                        sceneGraph.addGameObject(terrainGO);
                        // update outline
                        addGoToTree(null, terrainGO);

                        context.currScene.terrains.add(asset);
                        projectManager.saveProject(context);
                        Mundus.postEvent(new AssetImportEvent(asset));
                        Mundus.postEvent(new SceneGraphChangedEvent());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            rename.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (selectedGO != null) {
                        showRenameDialog();
                    }
                }
            });

            // duplicate node
            duplicate.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (selectedGO != null && !duplicate.isDisabled()) {
                        duplicateGO(selectedGO, selectedGO.getParent());
                        Mundus.postEvent(new SceneGraphChangedEvent());
                    }
                }
            });

            // delete game object
            delete.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (selectedGO != null) {
                        removeGo(selectedGO);
                        Mundus.postEvent(new SceneGraphChangedEvent());
                    }
                }
            });

            addItem(addEmpty);
            addItem(addTerrain);
            addItem(rename);
            addItem(duplicate);
            addItem(delete);
        }

        /**
         * Right click event opens menu and enables more options if selected
         * game object is active.
         *
         * @param go
         * @param x
         * @param y
         */
        public void show(GameObject go, float x, float y) {
            selectedGO = go;
            showMenu(Ui.getInstance(), x, y);

            // check if game object is selected
            if (selectedGO != null) {
                // Activate menu options for selected game objects
                rename.setDisabled(false);
                delete.setDisabled(false);
            } else {
                // disable MenuItems which only works with selected item
                rename.setDisabled(true);
                delete.setDisabled(true);
            }

            // terrainAsset can not be duplicated
            if (selectedGO == null || selectedGO.findComponentByType(Component.Type.TERRAIN) != null) {
                duplicate.setDisabled(true);
            } else {
                duplicate.setDisabled(false);
            }
        }

        public void showRenameDialog() {
            final Tree.Node node = tree.findNode(selectedGO);
            final TreeNode goNode = (TreeNode) node.getActor();

            InputDialog renameDialog = Dialogs.showInputDialog(Ui.getInstance(), "Rename", "",
                    new InputDialogAdapter() {
                        @Override
                        public void finished(String input) {
                            Log.trace(TAG, "Rename game object [{}] to [{}].", selectedGO, input);
                            // update sceneGraph
                            selectedGO.name = input;
                            // update Outline
                            //goNode.name.setText(input + " [" + selectedGO.id + "]");
                            goNode.name.setText(input);

                            Mundus.postEvent(new SceneGraphChangedEvent());
                        }
                    });
            // set position of dialog to menuItem position
            float nodePosX = node.getActor().getX();
            float nodePosY = node.getActor().getY();
            renameDialog.setPosition(nodePosX, nodePosY);
        }
    }
}