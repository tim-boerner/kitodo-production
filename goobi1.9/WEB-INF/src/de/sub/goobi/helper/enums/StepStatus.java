package de.sub.goobi.helper.enums;

import de.sub.goobi.helper.Helper;

/**
 * Enum for status of steps, each one with integer value for database, with
 * title and images for gui
 * 
 * @author Steffen Hankiewicz
 * @version 17.05.2009
 */
public enum StepStatus {
	/**
	 * Locked = step not startable
	 */
	LOCKED(0, "statusGesperrt", "red_10.gif", "red_15a.gif"),
	/**
	 * open = someone can beginn with this step
	 */
	OPEN(1, "statusOffen", "orange_10.gif", "orange_15a.gif"),
	/**
	 * inwork = someone is currently working on that step
	 */
	INWORK(2, "statusInBearbeitung", "yellow_10.gif", "yellow_15a.gif"),
	/**
	 * done = step is executed
	 */
	DONE(3, "statusAbgeschlossen", "green_10.gif", "green_15a.gif");

	private int value;
	private String title;
	private String imageSmall;
	private String imageBig;

	/**
	 * private constructor, initializes integer value, title, small and big image
	 */
	private StepStatus(int inValue, String inTitle, String smallImage, String bigImage) {
		value = inValue;
		title = inTitle;
		imageSmall = smallImage;
		imageBig = bigImage;
	}

	/**
	 * return integer value for database savings
	 * 
	 * @return value as integer
	 */
	public Integer getValue() {
		return value;
	}

	/**
	 * get title from status type
	 * 
	 * @return title as translated string for current locale from standard-jsf-messages
	 */
	public String getTitle() {
		return Helper.getTranslation(title);
	}

	/**
	 * get file name for small image
	 * 
	 * @return file name for small image
	 */
	public String getSmallImagePath() {
		return "/newpages/images/status/" + imageSmall;
	}

	/**
	 * get file name for big image
	 * 
	 * @return file name for big image
	 */
	public String getBigImagePath() {
		return "/newpages/images/status/" + imageBig;
	}

	/**
	 * retrieve StepStatus by integer value, neccessary for database handlings,
	 * where only integer is saved but not type safe
	 * 
	 * @param inValue
	 *            as integer value
	 * @return {@link StepStatus} for given integer
	 */
	public static StepStatus getStatusFromValue(Integer inValue) {
		for (StepStatus ss : values()) {
			if (ss.getValue() == inValue.intValue())
				return ss;
		}
		return LOCKED;
	}

}