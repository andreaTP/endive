;; Defines an empty table and calls through it.
(module
  (type $i32 (func (result i32)))
  (table (export "tab") 2 4 funcref)

  (func (export "call") (param i32) (result i32)
    (call_indirect (type $i32) (local.get 0)))
)
