;; Exports a table holding its function 3, an id the modules importing it don't have.
(module
  (table (export "tab") 1 funcref)

  (func $f0)
  (func $f1)
  (func $f2)
  (func $f3)

  (elem (i32.const 0) $f3)
)
