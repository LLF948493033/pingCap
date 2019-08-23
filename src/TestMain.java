import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class TestMain {
    public static void main(String[] args) {

        String[] aim = {"waxoSmCzLI","XBBVtJKEeDqG","FAACpXwzuoXkBuBLEId","PSBQmxqcE","DDAQYlOVhMLDH"//可以查到的数据
                         ,"waxoSmCzL","XBBVtJKEeDq","FAACpXwzuoXkBuBLEI","PSBQmxqc","DDAQYlOVhMLD"};//不能查到的数据
        for(int i=0; i<10; i++){//开十个线程同时查询
            int finalI = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println(aim[finalI]+" : "+Main.readFile(aim[finalI]));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }
}
