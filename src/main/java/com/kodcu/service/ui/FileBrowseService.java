package com.kodcu.service.ui;

import com.kodcu.other.Item;
import com.kodcu.service.FileWatchService;
import com.kodcu.service.PathOrderService;
import com.kodcu.service.PathResolverService;
import com.kodcu.service.ThreadService;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * Created by usta on 12.07.2014.
 */
@Component
public class FileBrowseService {

    private Logger logger = LoggerFactory.getLogger(FileBrowseService.class);

    private final PathOrderService pathOrder;
    private final ThreadService threadService;
    private final PathResolverService pathResolver;
    private final AwesomeService awesomeService;
    private final FileWatchService watchService;
    
    private TreeItem<Item> rootItem;
    
    private Integer lastSelectedItem;
    
    @Autowired
    public FileBrowseService(final PathOrderService pathOrder, final ThreadService threadService, final PathResolverService pathResolver,
            final AwesomeService awesomeService, final FileWatchService watchService) {
        this.pathOrder = pathOrder;
        this.threadService = threadService;
        this.pathResolver = pathResolver;
        this.awesomeService = awesomeService;
        this.watchService = watchService;
    }

    public void browse(final TreeView<Item> treeView, final Path browserPath) {

        int selectedIndex = treeView.getSelectionModel().getSelectedIndex();
        if (selectedIndex != -1)
            lastSelectedItem = selectedIndex;

        threadService.runActionLater(() -> {

            rootItem = new TreeItem<>(new Item(browserPath, String.format("Workdir (%s)", browserPath)), awesomeService.getIcon(browserPath));
            rootItem.setExpanded(true);
            treeView.setRoot(rootItem);

            threadService.runTaskLater(() -> {
                this.addPathToTree(browserPath, path -> {
                    threadService.runActionLater(r -> addToTreeView(path, rootItem));
                });

                threadService.runActionLater(r -> {
                    if (Objects.nonNull(lastSelectedItem))
                        treeView.getSelectionModel().select(lastSelectedItem);
                });

                watchService.registerWatcher(treeView, browserPath, this::browse);
            });
        });
    }


    public void addPathToTree(Path path, Consumer<Path> consumer) {

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path);) {

            StreamSupport
                    .stream(directoryStream.spliterator(), false)
                    .filter(p -> !pathResolver.isHidden(p))
                    .filter(pathResolver::isViewable)
                    .sorted(pathOrder::comparePaths)
                    .forEach(consumer);

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void addToTreeView(Path path, TreeItem<Item> whichItem) {

        if (pathResolver.isHidden(path))
            return;

        if (pathResolver.isViewable(path)) {

            TreeItem<Item> treeItem = new TreeItem<>(new Item(path), awesomeService.getIcon(path));
            whichItem.getChildren().add(treeItem);
        }

    }

}
