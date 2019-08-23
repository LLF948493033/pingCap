import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import java.util.concurrent.*;
import java.util.logging.Logger;

import static java.lang.Thread.currentThread;

public class Main {

    static Logger log = Logger.getLogger("pingCaplog");
    private static String sortSplitFilepath ="E:\\求职文件\\job\\pingCap\\testFile\\sortSplitFile\\";
//    private static ConcurrentSkipListMap<String,String> skipListMap = new ConcurrentSkipListMap<>();
    private final static int cacheSize = 65536000;//实测，每1000条数据大概64k，因此缓存4g的map，则可以存条数为4*1024*1024/0.064
    private static Map<String, String> lruMap = new LinkedHashMap<String, String>((int) Math.ceil(cacheSize / 0.75f) + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > cacheSize;
        }
    };
    private final static int bufferSize = 20*1024*1024;

    private static ExecutorService executorFixedThreadPool = Executors.newFixedThreadPool(8);


    private static class MyCallable implements Callable {
        String key;
        public MyCallable(String key){
            this.key = key;
        }
        @Override
        public Object call() throws Exception {
            String getValue = lruMap.get(key);
            if(getValue == null){//内存中跳表未查询到数据
                String path;
                if(key.length() != 1){
                    path = sortSplitFilepath+key.substring(0,2)+".txt";
                }else{
                    path = sortSplitFilepath+key+".txt";
                }
                if(!new File(path).exists()) return null;//不存在目标key的文件

                BufferedInputStream fis = new BufferedInputStream(new FileInputStream(path));
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis,"utf-8"),bufferSize);// 用缓冲读取文本文件

                String readLine;
                String[] readLineSplit;
                int compactTo;
                while ((readLine = reader.readLine()) != null){//对拆分并排序后的数据进行查找key
                    readLineSplit = readLine.split(",");
                    compactTo = readLineSplit[0].compareTo(key);
                    if(compactTo == 0){
                        fis.close();
                        reader.close();
                        lruMap.put(key,readLineSplit[1]);//将数据添加到内存的跳表中
                        return readLineSplit[1];
                    }else if(compactTo > 0){
                        fis.close();
                        reader.close();
                        return null;
                    }
                }
                fis.close();
                reader.close();
                return  null;
            }else{
                return getValue;
            }
        }
    }


    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {

        String splitFilepath ="E:\\求职文件\\job\\pingCap\\testFile\\splitFile\\";

        preproccess(splitFilepath);//文件预处理
        while(true);//等待
    }
    private static void preproccess(String splitFilepath)throws IOException{

        log.info("Start pretreatment...");
        String orginFilepath ="E:\\求职文件\\job\\pingCap\\testFile\\";
        File file = new File(orginFilepath+"text.txt");
        BufferedInputStream fis = new BufferedInputStream(new FileInputStream(file));
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis,"utf-8"),bufferSize);// 用缓冲读取文本文件  
        String readLine;
        String outFilepath;
        String[] readLineSplit;
        //第一个字符比他本身多占用三个字节
        log.info("Split file...");
        int count = 0;
        while ((readLine = reader.readLine()) != null){
            readLineSplit = readLine.split(",");
            if(!readLineSplit[0].equals("1")){
                outFilepath = splitFilepath + readLineSplit[1].substring(0,2).toLowerCase()+".txt";
            }else{
                outFilepath = splitFilepath + readLineSplit[1].toLowerCase()+".txt";
            }
            writeFileKV(readLineSplit[1],readLineSplit[3],outFilepath);
        }
        log.info("Sort file...");
        sortFileData(splitFilepath);
        fis.close();
        reader.close();
        log.info("End pretreatment.");
    }
    private static void writeFileKV(String key, String value, String outFilepath) throws IOException {
        File file=new File(outFilepath);
        if(!file.exists()){
            if(!file.createNewFile()){
                System.out.println("预处理创建文件失败..");
                return;
            }
        }
        FileOutputStream out=new FileOutputStream(file,true);
        out.write((key+ "," + value +"\r\n").getBytes("utf-8"));
        out.close();
    }

    private static void sortFileData(String splitFilepath) throws IOException {
        File file = new File(splitFilepath);
        File[] fs = file.listFiles();	//遍历path下的文件和目录，放在File数组中
        int fileNumberOfperThread = fs.length/8;//8个线程每个线程应该操作的文件数

        for(int i=0; i<7; i++){//8线程并发对文件重排序
            int finalI = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedInputStream fis;
                    BufferedReader reader;
                    String readLine;
                    String[] readLineSplit;
                    int start = finalI * fileNumberOfperThread,end = start + fileNumberOfperThread;
                    if(finalI == 6){
                        end = fs.length;
                    }
                    for(int j = start; j<end; j++){
                        try {
                            fis = new BufferedInputStream(new FileInputStream(fs[j]));
                            reader = new BufferedReader(new InputStreamReader(fis,"utf-8"),bufferSize);// 用缓冲读取文本文件
                            TreeMap<String,String> treeMap = new TreeMap<>();
                            while ((readLine = reader.readLine()) != null){
                                readLineSplit = readLine.split(",");
                                treeMap.put(readLineSplit[0],readLineSplit[1]);
                            }
                            String path = sortSplitFilepath +fs[j].getName();
                            treeMap.forEach((k,v) ->{
                                try {
                                    writeFileKV(k,v,path);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }finally {
                            fs[j].delete();
                        }
                    }
                }
            }).start();
        }
    }

    public static String readFile(String key) throws IOException, ExecutionException, InterruptedException {

        log.info("readKey:"+key);
        MyCallable myCallable = new MyCallable(key);
        FutureTask<Integer> ft = new FutureTask<>(myCallable);//FutureTask异步获取执行结果

        executorFixedThreadPool.submit(new Thread(ft,currentThread().getName()));//对外来访问的任务添加到线程池

        return String.valueOf(ft.get());

    }
}

