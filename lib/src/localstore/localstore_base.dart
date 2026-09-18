part of 'localstore.dart';

/// The entry point for accessing a [Localstore].
///
/// You can get an instance by calling [Localstore.instance], for example:
///
/// ```dart
/// final db = Localstore.instance;
/// ```
final class Localstore implements LocalstoreImpl {
  /// Directory the database lives in, replacing the application support
  /// directory when set.
  ///
  /// A portable copy of an app keeps everything it writes inside its own
  /// folder, and path_provider answers with a location in the user profile
  /// instead. An app can redirect path_provider, but this storage runs on its
  /// own isolate, where that registration does not apply — so the directory
  /// has to be handed across explicitly. Set it before the first read.
  static Directory? databaseDirectoryOverride;

  late final Future<Directory> _databaseDirectory = databaseDirectoryOverride != null
      ? Future.value(databaseDirectoryOverride!)
      : getApplicationSupportDirectory();
  final _delegate = DocumentRef._('');
  static final Localstore _localstore = Localstore._();

  /// Private initializer
  Localstore._();

  /// Returns an instance using the default [Localstore].
  static Localstore get instance => _localstore;

  Future<Directory> get databaseDirectory => _databaseDirectory;

  /// Clears the cache - needed only if filesystem has been manipulated directly
  void clearCache() {
    Utils.instance.clearCache();
  }

  @override
  CollectionRef collection(String path) {
    return CollectionRef(path, null, _delegate);
  }
}
