;; Puts a multi-value host function in its table.
(module
  (import "host" "none" (func $none (result i32 i32)))
  (table (export "tab") 1 funcref)

  (elem (i32.const 0) $none)
)
