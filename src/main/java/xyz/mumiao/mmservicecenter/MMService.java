package xyz.mumiao.mmservicecenter;

/**
 * Created by song on 15/6/15.
 */
public abstract class MMService implements MMServiceInterface{

    protected MMServiceState state = new MMServiceState();

    @Override
    public MMServiceState getServiceState() {
        return state;
    }

    @Override
    public void onServiceInit() {

    }

    @Override
    public void onServiceReloadData() {

    }

    @Override
    public void onServiceEnterForeground() {

    }

    @Override
    public void onServiceEnterBackground() {

    }

    @Override
    public void onServiceClearData() {

    }

    @Override
    public void onServiceTerminate() {
        state.isServiceRemoved = true;
    }
}
