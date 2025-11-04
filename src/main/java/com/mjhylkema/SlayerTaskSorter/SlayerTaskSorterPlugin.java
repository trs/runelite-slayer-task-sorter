package com.mjhylkema.SlayerTaskSorter;

import com.google.inject.Provides;
import static com.mjhylkema.SlayerTaskSorter.TaskEntry.extractFriendlyName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Slayer Task Sorter"
)
public class SlayerTaskSorterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SlayerTaskSorterConfig config;

	@Inject
	ClientThread clientThread;

	private Widget taskListsClickable;
	private Widget taskListsDrawable;
	private Widget taskListFrame;
	private Widget taskListTitle;

	private final static int HEIGHT = 22;

	private final List<TaskEntry> entries = new ArrayList<>();

	@Override
	public void startUp() {
		clientThread.invokeLater(this::initWidgets);
	}

	@Provides
	SlayerTaskSorterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SlayerTaskSorterConfig.class);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == InterfaceID.SLAYER_REWARDS_TASK_LIST) {
			initWidgets();

			if (taskListsDrawable == null || taskListsClickable == null) {
				return;
			}

			sortLater();
		}
	}
	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case SlayerTaskSorterConfig.KEY_ACTIVE_SORT_METHOD:
				sortLater();
				break;
			case SlayerTaskSorterConfig.KEY_REVERSE_SORT:
				sortLater();
				break;
			default:
				return;
		}
	}

	private void sortLater() {

		// Delay sorting to the next game tick after the widget updates
		clientThread.invokeLater(() -> {
			if (taskListsDrawable == null || taskListsClickable == null) {
				return;
			}

			entries.clear();

			Widget[] drawableChildren = taskListsDrawable.getChildren();
			if (drawableChildren == null) {
				return;
			}

			Widget[] clickableChildren = taskListsClickable.getChildren();
			if (clickableChildren == null) {
				return;
			}

			for (int i = 0; i < drawableChildren.length - 3; i++) {
				if (isTaskRow(drawableChildren, i)) {
					String friendlyName = extractFriendlyName(drawableChildren[i].getText());
					entries.add(new TaskEntry(
						this, getClickableWidgetFromName(clickableChildren, friendlyName),
						drawableChildren[i],
						drawableChildren[i + 1],
						drawableChildren[i + 2],
						drawableChildren[i + 3]
					));
				}
			}

			sort();

			drawHeader();
		});
	}

	private Widget buildSortButton(Widget parent, String title, int width, SortMethod method) {
		Widget sortButtonContainer = parent.createChild(-1, WidgetType.LAYER);
		sortButtonContainer.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		sortButtonContainer.setYPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		sortButtonContainer.setWidthMode(WidgetSizeMode.ABSOLUTE);
		sortButtonContainer.setHeightMode(WidgetSizeMode.ABSOLUTE);
		sortButtonContainer.setOriginalWidth(width);
		sortButtonContainer.setOriginalHeight(24);
		sortButtonContainer.setHasListener(true);
		sortButtonContainer.setAction(0, method.name);
		sortButtonContainer.setOnOpListener((JavaScriptCallback)((e) -> handleSortButtonOp(method)));
		sortButtonContainer.revalidate();

		Widget sortButtonText = sortButtonContainer.createChild(-1, WidgetType.TEXT);
		sortButtonText.setText(title);
		sortButtonText.setTextColor(0xff981f);
		sortButtonText.setFontId(FontID.BOLD_12);
		sortButtonText.setTextShadowed(true);
		sortButtonText.setXTextAlignment(WidgetTextAlignment.LEFT);
		sortButtonText.setYTextAlignment(WidgetTextAlignment.CENTER);
		sortButtonText.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		sortButtonText.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		sortButtonText.setOriginalX(4);
		sortButtonText.setWidthMode(WidgetSizeMode.ABSOLUTE);
		sortButtonText.setHeightMode(WidgetSizeMode.MINUS);
		sortButtonText.setOriginalWidth(width - 12);
		sortButtonText.revalidate();

		Widget sortButtonIcon = sortButtonContainer.createChild(-1, WidgetType.GRAPHIC);
		if (config.sortingMethod() == method) {
			sortButtonIcon.setHidden(false);
			if (config.reverseSort()) {
				sortButtonIcon.setSpriteId(SpriteID.Sortarrows.ASCENDING);
			} else {
				sortButtonIcon.setSpriteId(SpriteID.Sortarrows.DESCENDING);
			}
		} else {
			sortButtonIcon.setHidden(true);
		}
		sortButtonIcon.setXTextAlignment(WidgetTextAlignment.CENTER);
		sortButtonIcon.setYTextAlignment(WidgetTextAlignment.CENTER);
		sortButtonIcon.setOriginalWidth(7);
		sortButtonIcon.setOriginalHeight(5);
		sortButtonIcon.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		sortButtonIcon.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
		sortButtonIcon.setOriginalX(10);
		sortButtonIcon.setWidthMode(WidgetSizeMode.ABSOLUTE);
		sortButtonIcon.setHeightMode(WidgetSizeMode.ABSOLUTE);
		sortButtonIcon.revalidate();

		return sortButtonContainer;
	}

	private void initWidgets() {
		taskListsDrawable = client.getWidget(InterfaceID.SlayerRewardsTaskList.DRAWABLE);
		taskListsClickable = client.getWidget(InterfaceID.SlayerRewardsTaskList.CLICKABLE);
		taskListFrame = client.getWidget(InterfaceID.SlayerRewardsTaskList.FRAME);

		clientThread.invokeLater(() -> {
			if (taskListFrame == null) {
				return;
			}
			taskListTitle = taskListFrame.getChild(1);

			if (taskListTitle == null) {
				return;
			}

			taskListTitle.setHidden(true);
			taskListTitle.revalidate();

			drawHeader();
		});
	}

	private void drawHeader() {
		var taskListWorld = client.getWidget(InterfaceID.SlayerRewardsTaskList.WORLD);
		if (taskListWorld == null) {
			return;
		}

		taskListWorld.deleteAllChildren();

		Widget taskHeaderContainer = taskListWorld.createChild(-1, WidgetType.LAYER);
		taskHeaderContainer.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		taskHeaderContainer.setYPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		taskHeaderContainer.setWidthMode(WidgetSizeMode.MINUS);
		taskHeaderContainer.setHeightMode(WidgetSizeMode.ABSOLUTE);
		taskHeaderContainer.setOriginalX(6);
		taskHeaderContainer.setOriginalY(6);
		taskHeaderContainer.setOriginalWidth(12);
		taskHeaderContainer.setOriginalHeight(24);
		taskHeaderContainer.setHasListener(true);
		taskHeaderContainer.revalidate();

		var nameSortButton = buildSortButton(taskHeaderContainer, "Slayer Task", 106, SortMethod.SORT_BY_NAME);
		nameSortButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		nameSortButton.revalidate();

		var weightSortButton = buildSortButton(taskHeaderContainer, "Weight", 74, SortMethod.SORT_BY_WEIGHTING);
		weightSortButton.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
		weightSortButton.setOriginalX(6);
		weightSortButton.revalidate();
	}

	private Widget getClickableWidgetFromName(Widget[] clickableWidgets, String name) {
		for (Widget widget : clickableWidgets) {
			if (Text.removeTags(widget.getName()).equals(name)) {
				return widget;
			}
		}
		return null;
	}

	private boolean isTaskRow(Widget[] widgets, int index) {
		return (widgets[index].getType() == 4 && widgets[index].getText() != null) &&
				widgets[index + 1].getType() == 3 &&
				widgets[index + 2].getType() == 5 &&
			   (widgets[index + 3].getType() == 4 && widgets[index + 3].getText() != null);
	}

	private void sort() {
		Comparator<TaskEntry> comparator = null;
		switch (config.sortingMethod()) {
			case SORT_BY_NAME:
				comparator = Comparator.comparing(TaskEntry::getFriendlyName, config.reverseSort()
					? Comparator.nullsLast(Comparator.reverseOrder())   // ascending
					: Comparator.nullsLast(Comparator.naturalOrder())   // descending
					);
				break;
			case SORT_BY_WEIGHTING:
				comparator = Comparator.comparing(TaskEntry::getWeighting, config.reverseSort()
					? Comparator.nullsLast(Comparator.naturalOrder())   // ascending
					: Comparator.nullsLast(Comparator.reverseOrder())   // descending
					)
					.thenComparing(entry -> entry.getStatus().getSortOrder())
					.thenComparing(TaskEntry::getFriendlyName);
				break;
		}

		if (comparator != null) {
			entries.sort(comparator);
		}

		for (int i = 0; i < entries.size(); i++) {
			entries.get(i).setOriginalYAndRevalidate(HEIGHT * i);
		}
	}

	private void handleSortButtonOp(SortMethod method) {
		if (config.sortingMethod() == method) {
			config.setReverseOrder(!config.reverseSort());
		} else {
			config.setReverseOrder(false);
		}
		config.setSortingMethod(method);
		sortLater();
	}
}
