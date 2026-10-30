;; Copies its exported table within itself and into a second table, and calls through either.
(module
  (type $i32 (func (result i32)))
  (table $a (export "tab") 4 funcref)
  (table $b 4 funcref)

  (func (export "copyAToB")
    (table.copy $b $a (i32.const 0) (i32.const 0) (i32.const 4)))

  (func (export "shiftA")
    (table.copy $a $a (i32.const 1) (i32.const 0) (i32.const 3)))

  (func (export "callA") (param i32) (result i32)
    (call_indirect $a (type $i32) (local.get 0)))

  (func (export "callB") (param i32) (result i32)
    (call_indirect $b (type $i32) (local.get 0)))
)
