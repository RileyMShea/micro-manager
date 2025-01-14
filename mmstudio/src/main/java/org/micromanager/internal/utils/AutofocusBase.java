package org.micromanager.internal.utils;

import java.util.ArrayList;
import org.micromanager.AutofocusPlugin;
import org.micromanager.UserProfile;
import org.micromanager.internal.MMStudio;

/**
 * Utility class contain code common to autofocus plugins.
 */
public abstract class AutofocusBase implements AutofocusPlugin {

   private final ArrayList<PropertyItem> properties_;
   private static final String AF_UNIMPLEMENTED_FUNCTION = "Operation not supported.";

   public AutofocusBase() {
      properties_ = new ArrayList<>();
   }

   protected void createProperty(String name) {
      PropertyItem p = new PropertyItem();
      p.name = name;
      p.device = getName();
      properties_.add(p);
   }

   protected void createProperty(String name, String value) {
      PropertyItem p = new PropertyItem();
      p.name = name;
      p.value = value;
      p.device = getName();
      properties_.add(p);
   }

   protected void createProperty(String name, String value, String[] allowed) {
      PropertyItem p = new PropertyItem();
      p.allowed = allowed;
      p.name = name;
      p.value = value;
      p.device = getName();
      properties_.add(p);
   }

   /**
    * Add property to the list of device properties.
    * This is the most general method where all property features can be controlled.
    *
    * @param p - property object
    */
   protected void addProperty(PropertyItem p) {
      properties_.add(p);
   }

   /**
    * Get all property names (keys).
    *
    * @return - an array of property names
    */
   @Override
   public String[] getPropertyNames() {
      String[] propName = new String[properties_.size()];
      for (int i = 0; i < properties_.size(); i++) {
         propName[i] = properties_.get(i).name;
      }
      return propName;

      //return properties_.keySet().toArray(new String[0]);

   }

   /**
    * Get value for a given property name.
    */
   @Override
   public String getPropertyValue(String name) throws MMException {
      for (PropertyItem propertyItem : properties_) {
         if (name.equals(propertyItem.name)) {
            return propertyItem.value;
         }
      }
      throw new MMException("Unknown property: " + name);
   }

   /**
    * Get property for a given property name.
    */
   @Override
   public PropertyItem getProperty(String name) throws MMException {
      for (PropertyItem propertyItem : properties_) {
         if (name.equals(propertyItem.name)) {
            return propertyItem;
         }
      }
      throw new MMException("Unknown property: " + name);

   }

   @Override
   public void setProperty(PropertyItem p) throws MMException {
      for (int i = 0; i < properties_.size(); i++) {
         if (p.name.equals(properties_.get(i).name)) {
            properties_.set(i, p);
            return;
         }
      }
      properties_.add(p);
   }

   /**
    * Sets value for a given property name.
    * This method will not check if the value is allowed or not,
    * or whether it conforms to the property limits.
    * It is assumed that the caller will take care of that using appropriate
    * property information (see Property class)
    */
   @Override
   public void setPropertyValue(String name, String value) throws MMException {
      for (PropertyItem propertyItem : properties_) {
         if (name.equals(propertyItem.name)) {
            propertyItem.value = value;
            return;
         }
      }
      throw new MMException("Unknown property: " + name);
   }

   @Override
   public PropertyItem[] getProperties() {
      return properties_.toArray(new PropertyItem[properties_.size()]);
   }

   @Override
   public void saveSettings() {
      UserProfile profile = MMStudio.getInstance().profile();
      for (PropertyItem propertyItem : properties_) {
         profile.getSettings(this.getClass()).putString(
               propertyItem.name, propertyItem.value);
      }
   }

   /**
    * Restore settings from the Profile.
    */
   public void loadSettings() {
      UserProfile profile = MMStudio.getInstance().profile();
      for (PropertyItem propertyItem : properties_) {
         propertyItem.value = profile.getSettings(this.getClass())
               .getString(propertyItem.name, propertyItem.value);
      }
   }

   /**
    * Not sure what this is used for.
    *
    * @param msg not used.
    */
   public void dumpProperties(String msg) {
      ReportingUtils.logMessage(msg);
      for (PropertyItem propertyItem : properties_) {
         propertyItem.dump();
      }
   }

   @Override
   public void enableContinuousFocus(boolean enable) throws MMException {
      throw new MMException(AF_UNIMPLEMENTED_FUNCTION);
   }

   @Override
   public boolean isContinuousFocusEnabled() throws MMException {
      return false;
   }

   @Override
   public boolean isContinuousFocusLocked() throws MMException {
      return false;
   }

   @Override
   public void initialize() {
      loadSettings();
   }

}
