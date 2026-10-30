;; Fills its exported table with its function, which returns 42, from the table's initializer.
(module
  (global $g i32 (i32.const 42))

  (func $answer (result i32) global.get $g)

  (table (export "tab") 2 funcref (ref.func $answer))
)
