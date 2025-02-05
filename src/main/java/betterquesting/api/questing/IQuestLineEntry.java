package betterquesting.api.questing;

import betterquesting.api2.storage.INBTSaveLoad;
import net.minecraft.nbt.NBTTagCompound;

public interface IQuestLineEntry extends INBTSaveLoad<NBTTagCompound> {
    @Deprecated
    int getSize();

    @Deprecated
    void setSize(int size);

    int getSizeX();

    int getSizeY();

    int getPosX();

    int getPosY();

    void setPosition(int posX, int posY);

    void setSize(int sizeX, int sizeY);
}
