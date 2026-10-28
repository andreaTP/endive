;; Exports a table whose index 1 holds its own function, which returns -1, and calls through it.
(module
  (type $i32 (func (result i32)))
  (table (export "tab") 2 funcref)
  (global $g i32 (i32.const -1))

  (func $own (result i32) global.get $g)

  (elem (i32.const 1) $own)

  (func (export "call") (param i32) (result i32)
    (call_indirect (type $i32) (local.get 0)))
)
