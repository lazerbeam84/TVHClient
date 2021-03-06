package org.tvheadend.tvhclient.data.source;

import android.arch.lifecycle.LiveData;

import java.util.List;

interface DataSourceInterface<T> {

    void addItem(T item);

    void updateItem(T item);

    void removeItem(T item);

    LiveData<Integer> getLiveDataItemCount();

    LiveData<List<T>> getLiveDataItems();

    LiveData<T> getLiveDataItemById(Object id);

    T getItemById(Object id);

    List<T> getItems();
}
