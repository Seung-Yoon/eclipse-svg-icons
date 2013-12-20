import java.io.File;
import java.net.URI;


public class MissingIconSearch {

    private static int missingIcons = 0;
    
    /**
     * Walks a directory searching for gifs without svg icons.
     * 
     * @param resources the base search dir
     * @param filter
     */
    private static void printMissingIcons(File resources, File root, String filter) {
        outer:
        for (File child : resources.listFiles()) {
            if(child.isDirectory()) {
                printMissingIcons(child, root, filter);
                continue;
            }

            String childName = child.getName();
            if(childName.endsWith("gif")) {
                String baseName = childName.substring(0, childName.lastIndexOf('.'));
                baseName += ".svg";
                File parentFile = child.getParentFile();
                // Search for a matching svg file
                boolean found = false;
                for(File siblings : parentFile.listFiles()) {
                    String siblingName = siblings.getName();
                    if(siblingName.equals(baseName)) {
                        continue outer;
                    }
                }
                
                if(!found) {
                    URI res = root.toURI();
                    URI uri = child.toURI();
                    URI relativize = res.relativize(uri);
                    String path = relativize.getPath();
                    if(filter == null || path.contains(filter)) {
                        System.out.println(relativize.getPath());
                    }
                    
                    missingIcons++;
                }
            }
        }
    }
    
    public static void main(String[] args) {
        File root = new File("src/main/resources");
        printMissingIcons(root, root, null);
        System.out.println("Missing Icons: " + missingIcons);
    }

}
