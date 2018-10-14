/* Copyright (c) 2006 FastJAX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.fastjax.lang;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code PackageLoader} is a class used to discover and load classes in a
 * package. Given a package name, the {@code PackageLoader} should attempt to
 * locate and/or load the classes of the package. The {@code PackageLoader} uses
 * a {@link ClassLoader}, either specified or default, for the discovery of
 * packages and loading of classes.
 */
public class PackageLoader {
  private static final Logger logger = LoggerFactory.getLogger(PackageLoader.class);

  private static final Map<ClassLoader,PackageLoader> instances = new HashMap<>();

  private static final BiPredicate<Path,BasicFileAttributes> classPredicate = new BiPredicate<Path,BasicFileAttributes>() {
    @Override
    public boolean test(final Path t, final BasicFileAttributes u) {
      return u.isRegularFile() && t.toString().endsWith(".class");
    }
  };

  /**
   * Returns a {@code PackageLoader} that uses the system class loader for the
   * discovery of packages and the loading of their classes.
   *
   * @return The {@code PackageLoader} that uses the system class loader for the
   *         discovery of packages and the loading of their classes.
   */
  public static PackageLoader getSystemPackageLoader() {
    return getPackageLoader(ClassLoader.getSystemClassLoader());
  }

  /**
   * Returns a {@code PackageLoader} that uses the context class loader for the
   * discovery of packages and the loading of their classes.
   *
   * @return The {@code PackageLoader} that uses the context class loader for
   *         the discovery of packages and the loading of their classes.
   */
  public static PackageLoader getContextPackageLoader() {
    return getPackageLoader(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns a {@code PackageLoader} that uses the specified {@code classLoader}
   * for the discovery of packages and the loading of their classes.
   *
   * @return The {@code PackageLoader} that uses the specified
   *         {@code classLoader} for the discovery of packages and the loading
   *         of their classes.
   */
  public static PackageLoader getPackageLoader(final ClassLoader classLoader) {
    PackageLoader packageLoader = instances.get(classLoader);
    if (packageLoader != null)
      return packageLoader;

    synchronized (instances) {
      packageLoader = instances.get(classLoader);
      if (packageLoader != null)
        return packageLoader;

      instances.put(classLoader, packageLoader = new PackageLoader(classLoader));
      return packageLoader;
    }
  }

  private final ClassLoader classLoader;

  private PackageLoader(final ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  /**
   * Load and initialize each class in the specified {@link Package} and its
   * sub-packages.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param pkg The {@link Package}.
   * @return Set of classes discovered, loaded, and initialized.
   * @throws PackageNotFoundException If {@code pkg} cannot be found in the
   *           class loader of this {@code PackageLoader} instance.
   * @throws NullPointerException If {@code pkg} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final Package pkg) throws PackageNotFoundException {
    return loadPackage(pkg.getName(), true, true);
  }

  /**
   * Load and initialize each class in the specified {@link Package} and its
   * sub-packages.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param pkg The {@link Package}.
   * @param initialize {@link Predicate} specifying which discovered classes to
   *          initialize, or {@code null} to initialize discovered all classes.
   * @throws PackageNotFoundException If {@code pkg} cannot be found in the
   *           class loader of this {@code PackageLoader} instance.
   * @throws NullPointerException If {@code pkg} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public void loadPackage(final Package pkg, final Predicate<Class<?>> initialize) throws PackageNotFoundException {
    PackageLoader.loadPackage(pkg.getName(), true, false, initialize, classLoader);
  }

  /**
   * Load and initialize each class in the specified {@link Package} and its
   * sub-packages.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param pkg The {@link Package}.
   * @param initialize If {@code true}, initialize discovered classes; if
   *          {@code false}, do not initialize discovered classes.
   * @return Set of discovered classes, whether they were initialized or not.
   * @throws PackageNotFoundException If {@code pkg} cannot be found in the
   *           class loader of this {@code PackageLoader} instance.
   * @throws NullPointerException If {@code pkg} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final Package pkg, final boolean initialize) throws PackageNotFoundException {
    return loadPackage(pkg.getName(), true, initialize);
  }

  /**
   * Load and initialize each class in the specified {@link Package}.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param pkg The {@link Package}.
   * @param includeSubPackages If {@code true}, classes from sub-packages of the
   *          package specified by {@code name} will be included in the returned
   *          {@link Set} (regardless of the value of {@code initialize}); if
   *          {@code false}, classes of sub-packages will not be included.
   * @param initialize If {@code true}, initialize discovered classes; if
   *          {@code false}, do not initialize discovered classes.
   * @return Set of discovered classes, whether they were initialized or not.
   * @throws PackageNotFoundException If {@code pkg} cannot be found in the
   *           class loader of this {@code PackageLoader} instance.
   * @throws NullPointerException If {@code pkg} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final Package pkg, final boolean includeSubPackages, final boolean initialize) throws PackageNotFoundException {
    return loadPackage(pkg.getName(), includeSubPackages, initialize);
  }

  /**
   * Load and initialize each class in the package specified by {@code name}
   * (including classes in sub-packages).
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param name The name of the package.
   * @return Set of classes discovered, loaded, and initialized.
   * @throws PackageNotFoundException If the package specified by {@code name}
   *           cannot be found in the class loader of this {@code PackageLoader}
   *           instance.
   * @throws NullPointerException If {@code name} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final String name) throws PackageNotFoundException {
    return loadPackage(name, true, true);
  }

  /**
   * Load and initialize each class in the package specified by {@code name}
   * (including classes in sub-packages).
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param name The name of the package.
   * @param initialize If {@code true}, initialize discovered classes; if
   *          {@code false}, do not initialize discovered classes.
   * @return Set of classes discovered, loaded, and initialized.
   * @throws PackageNotFoundException If the package specified by {@code name}
   *           cannot be found in the class loader of this {@code PackageLoader}
   *           instance.
   * @throws NullPointerException If {@code name} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final String name, final boolean initialize) throws PackageNotFoundException {
    return loadPackage(name, true, initialize);
  }

  /**
   * Load and initialize each class in the package specified by {@code name}
   * (including classes in sub-packages).
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param name The name of the package.
   * @param initialize {@link Predicate} specifying which discovered classes to
   *          initialize, or {@code null} to initialize discovered all classes.
   * @throws PackageNotFoundException If the package specified by {@code name}
   *           cannot be found in the class loader of this {@code PackageLoader}
   *           instance.
   * @throws NullPointerException If {@code name} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public void loadPackage(final String name, final Predicate<Class<?>> initialize) throws PackageNotFoundException {
    PackageLoader.loadPackage(name, true, false, initialize, classLoader);
  }

  /**
   * Load and initialize each class in the package specified by {@code name}.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param name The name of the package.
   * @param includeSubPackages If {@code true}, classes from sub-packages of the
   *          package specified by {@code name} will be included in the returned
   *          {@link Set} (regardless of the value of {@code initialize}); if
   *          {@code false}, classes of sub-packages will not be included.
   * @param initialize {@link Predicate} specifying which discovered classes to
   *          initialize, or {@code null} to initialize discovered all classes.
   * @throws PackageNotFoundException If the package specified by {@code name}
   *           cannot be found in the class loader of this {@code PackageLoader}
   *           instance.
   * @throws NullPointerException If {@code name} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public void loadPackage(final String name, final boolean includeSubPackages, final Predicate<Class<?>> initialize) throws PackageNotFoundException {
    PackageLoader.loadPackage(name, includeSubPackages, false, initialize, classLoader);
  }

  /**
   * Load and initialize each class in the package specified by {@code name}.
   * <p>
   * Packages are discovered by searching for path resources in the
   * {@link PackageLoader#classLoader} of this {@code PackageLoader}. If the
   * package exists in multiple locations, such as jar files and/or directories,
   * each of the resource locations will be used to load all classes from each
   * resource.
   *
   * @param name The name of the package.
   * @param includeSubPackages If {@code true}, classes from sub-packages of the
   *          package specified by {@code name} will be included in the returned
   *          {@link Set} (regardless of the value of {@code initialize}); if
   *          {@code false}, classes of sub-packages will not be included.
   * @param initialize If {@code true}, initialize discovered classes; if
   *          {@code false}, do not initialize discovered classes.
   * @return Set of classes discovered, loaded, and initialized.
   * @throws PackageNotFoundException If the package specified by {@code name}
   *           cannot be found in the class loader of this {@code PackageLoader}
   *           instance.
   * @throws NullPointerException If {@code name} is null.
   * @see Class#forName(String,boolean,ClassLoader)
   */
  public Set<Class<?>> loadPackage(final String name, final boolean includeSubPackages, final boolean initialize) throws PackageNotFoundException {
    final Set<Class<?>> classes = new HashSet<>();
    PackageLoader.loadPackage(name, includeSubPackages, initialize, new Predicate<Class<?>>() {
      @Override
      public boolean test(final Class<?> t) {
        classes.add(t);
        return true;
      }
    }, classLoader);
    return classes;
  }

  private static void loadPackage(final String packageName, final boolean includeSubPackages, final boolean initialize, final Predicate<Class<?>> filter, final ClassLoader classLoader) throws PackageNotFoundException {
    try {
      final String location = packageName.replace('.', '/');
      final Enumeration<URL> urls = classLoader.getResources(location);
      final ArrayList<URL> resources = new ArrayList<>(1);
      while (urls.hasMoreElements())
        resources.add(urls.nextElement());

      if (resources.size() == 0)
        throw new PackageNotFoundException(packageName.length() > 0 ? packageName : "<default>");

      // Reverse the order of resources, because the resources from the classLoader's parent,
      // and its parent, and its parent... are listed first -- thus, if the resource belongs to
      // the classLoader, it is guaranteed to always be the last element in the list
      for (int i = resources.size() - 1; i >= 0; --i) {
        final URL url = resources.get(i);
        final Set<String> classNames = new HashSet<>();
        if ("file".equals(url.getProtocol())) {
          String decodedUrl;
          try {
            decodedUrl = URLDecoder.decode(url.getPath(), "UTF-8");
          }
          catch (final UnsupportedEncodingException e) {
            decodedUrl = url.getPath();
          }

          PackageLoader.loadDirectory(classNames, new File(decodedUrl), packageName, includeSubPackages);
        }
        else if ("jar".equals(url.getProtocol())) {
          PackageLoader.loadJar(classNames, url, packageName, includeSubPackages);
        }
        else {
          throw new UnsupportedOperationException("Unsupported protocol in URL: " + url.toExternalForm());
        }

        for (final String className : classNames) {
          try {
            final Class<?> cls = Class.forName(className, initialize, classLoader);
            if (filter != null && filter.test(cls) || initialize)
              Class.forName(className, true, classLoader);
          }
          catch (final ClassNotFoundException | VerifyError e) {
            if (logger.isTraceEnabled())
              logger.trace("Problem loading package: " + (packageName.length() > 0 ? packageName : "<default>"), e);
          }
          catch (final NoClassDefFoundError e) {
          }
        }
      }
    }
    catch (final IOException e) {
      throw new PackageNotFoundException(e.getMessage(), e);
    }
  }

  private static void loadDirectory(final Set<String> classNames, final File directory, final String packageName, final boolean includeSubPackages) throws IOException {
    final Path path = directory.toPath();
    final Consumer<Path> consumer = new Consumer<Path>() {
      final String packagePrefix = packageName.length() > 0 ? packageName + "." : "";

      @Override
      public void accept(final Path t) {
        final String classFile = path.relativize(t).toString();
        final String className = packagePrefix + classFile.substring(0, classFile.length() - 6).replace(File.separatorChar, '.');
        classNames.add(className);
      }
    };

    if (includeSubPackages)
      Files.find(path, Integer.MAX_VALUE, classPredicate).forEach(consumer);
    else
      Files.list(path).forEach(consumer);
  }

  private static void loadJar(final Set<String> classNames, final URL url, final String packageName, final boolean includeSubPackages) throws PackageNotFoundException {
    final JarURLConnection jarURLConnection;
    final JarFile jarFile;
    try {
      jarURLConnection = (JarURLConnection)url.openConnection();
      jarURLConnection.setUseCaches(false);
      jarFile = jarURLConnection.getJarFile();
    }
    catch (final IOException e) {
      throw new PackageNotFoundException(packageName.length() > 0 ? packageName : "<default>", e);
    }

    final String packagePrefix = packageName.length() > 0 ? packageName + "." : "";
    final String entryName = jarURLConnection.getEntryName();
    final Enumeration<JarEntry> enumeration = jarFile.entries();
    while (enumeration.hasMoreElements()) {
      final String entry = enumeration.nextElement().getName();
      if (entry.startsWith(entryName) && entry.endsWith(".class")) {
        final String className = (entry.charAt(0) == '/' ? entry.substring(1, entry.length() - 6) : entry.substring(0, entry.length() - 6)).replace('/', '.');
        if (className.startsWith(packagePrefix) && (includeSubPackages || className.indexOf(".", packagePrefix.length() + 1) < 0))
          classNames.add(className);
      }
    }
  }
}