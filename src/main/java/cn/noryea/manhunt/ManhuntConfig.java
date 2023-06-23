package cn.noryea.manhunt;

import com.google.gson.*;
import net.minecraft.ChatFormatting;

import java.io.*;

import static cn.noryea.manhunt.Manhunt.LOGGER;

public class ManhuntConfig {

  private ManhuntConfig() {
  }

  //Config instance.
  public static final ManhuntConfig INSTANCE = new ManhuntConfig();
  private final File confFile = new File("./config/manhunt.json");
  Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private ChatFormatting huntersColor = ChatFormatting.RED;
  private ChatFormatting runnersColor = ChatFormatting.GREEN;
  private int delay = 0;
  private boolean runnersWinOnDragonDeath = true;

  private boolean showTitle = true;
  private boolean showRunnerDimension = true;

  private boolean automaticCompassUpdate = false;
  private int automaticCompassUpdateDelay = 0;

  //Getters
  public ChatFormatting getHuntersColor() {
    return huntersColor;
  }

  public ChatFormatting getRunnersColor() {
    return runnersColor;
  }

  public int getDelay() {
    return delay;
  }

  public boolean isRunnersWinOnDragonDeath() {
    return runnersWinOnDragonDeath;
  }

  public boolean isShowTitle() {
    return showTitle;
  }

  public boolean isShowRunnerDimension() {
    return showRunnerDimension;
  }

  public boolean isAutomaticCompassUpdate() {
    return automaticCompassUpdate;
  }

  public int getAutomaticCompassDelay() {
    return automaticCompassUpdateDelay;
  }

  //Setters
  public void setHuntersColor(ChatFormatting color) {
    if (color == null) color = ChatFormatting.WHITE;
    huntersColor = color;
    save();
  }

  public void setRunnersColor(ChatFormatting color) {
    if (color == null) color = ChatFormatting.WHITE;
    runnersColor = color;
    save();
  }

  public void setDelay(int time) {
    delay = time;
    save();
  }

  public void setRunnersWinOnDragonDeath(boolean bool) {
    runnersWinOnDragonDeath = bool;
    save();
  }

  public void setShowTitle(boolean bool) {
    showTitle = bool;
    save();
  }

  public void setShowRunnerDimension(boolean bool) {
    showRunnerDimension = bool;
    save();
  }

  public void setAutomaticCompassUpdate(boolean bool) {
    automaticCompassUpdate = bool;
    save();
  }

  public void setAutomaticCompassDelay(int timeInSeconds) {
    automaticCompassUpdateDelay = timeInSeconds;
    save();
  }

  public void load() {
    if (!confFile.exists() || confFile.length() == 0) save();
    try {
      JsonObject jo = gson.fromJson(new FileReader(confFile), JsonObject.class);
      JsonElement je;

      if ((je = jo.get("huntersColor")) != null) huntersColor = ChatFormatting.getByName(je.getAsString());
      if ((je = jo.get("runnersColor")) != null) runnersColor = ChatFormatting.getByName(je.getAsString());
      if ((je = jo.get("compassDelay")) != null) delay = je.getAsInt();
      if ((je = jo.get("runnersWinOnDragonDeath")) != null) runnersWinOnDragonDeath = je.getAsBoolean();
      if ((je = jo.get("showTitle")) != null) showTitle = je.getAsBoolean();
      if ((je = jo.get("showRunnerDimension")) != null) showRunnerDimension = je.getAsBoolean();

      if ((je = jo.get("automaticCompassUpdate")) != null) automaticCompassUpdate = je.getAsBoolean();
      if ((je = jo.get("automaticCompassUpdateDelay")) != null) automaticCompassUpdateDelay = je.getAsInt();
    } catch (FileNotFoundException ex) {
      LOGGER.trace("Couldn't load configuration file", ex);
    }
    save();
  }

  public void save() {
    try {
      if (!confFile.exists()) {
        confFile.getParentFile().mkdirs();
        confFile.createNewFile();
      }

      JsonObject jo = new JsonObject();
      jo.add("_ColorsList", new JsonPrimitive(String.join(", ", ChatFormatting.getNames(true, false))));
      jo.add("huntersColor", new JsonPrimitive(huntersColor.getName()));
      jo.add("runnersColor", new JsonPrimitive(runnersColor.getName()));
      jo.add("compassDelay", new JsonPrimitive(delay));
      jo.add("runnersWinOnDragonDeath", new JsonPrimitive(runnersWinOnDragonDeath));
      jo.add("showTitle", new JsonPrimitive(showTitle));
      jo.add("showRunnerDimension", new JsonPrimitive(showRunnerDimension));
      jo.add("automaticCompassUpdate", new JsonPrimitive(automaticCompassUpdate));
      jo.add("automaticCompassUpdateDelay", new JsonPrimitive(automaticCompassUpdateDelay));

      PrintWriter printwriter = new PrintWriter(new FileWriter(confFile));
      printwriter.print(gson.toJson(jo));
      printwriter.close();
    } catch (IOException ex) {
      LOGGER.trace("Couldn't save configuration file", ex);
    }
  }
}
