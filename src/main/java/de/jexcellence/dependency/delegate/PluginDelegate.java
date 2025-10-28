package de.jexcellence.dependency.delegate;

import org.jetbrains.annotations.NotNull;

public interface PluginDelegate<T> {

    @NotNull T getImpl();

    void onLoad();

    void onEnable();

    void onDisable();
}